package com.precious.syncres.services;

import com.precious.syncres.entities.CvDocument;
import com.precious.syncres.entities.JdSnapshot;
import com.precious.syncres.entities.MatchJobResult;
import com.precious.syncres.entities.User;
import com.precious.syncres.repositories.CvDocumentRepository;
import com.precious.syncres.repositories.JdSnapshotRepository;
import com.precious.syncres.repositories.MatchJobResultRepository;
import com.precious.syncres.repositories.UserRepository;
import com.precious.syncres.shared.dto.MatchJobAcceptedDto;
import com.precious.syncres.shared.dto.MatchRequestDto;
import com.precious.syncres.shared.dto.MatchResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchJobService {

    private final MatchJobResultRepository matchJobResultRepository;
    private final JobScheduler jobScheduler;
    private final CvDocumentRepository cvDocumentRepository;
    private final JdSnapshotRepository jdSnapshotRepository;
    private final UserRepository userRepository;
    private final JdScraperService jdScraperService;
    private final MatchScorerService matchScorerService;
    private final CvRewriterService cvRewriterService;
    private final PdfGeneratorService pdfGeneratorService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MatchJobAcceptedDto enqueueMatch(MatchRequestDto request, UUID userId, String sessionId) {
        MatchJobResult jobResult = MatchJobResult.builder()
                .jobrunrJobId("PENDING")
                .userId(userId != null ? User.builder().id(userId).build() : null)
                .sessionId(sessionId)
                .status(MatchJobResult.JobStatus.PENDING)
                .build();
        
        jobResult = matchJobResultRepository.save(jobResult);
        final UUID jobResultId = jobResult.getId();

        String jobrunrId = jobScheduler.enqueue(() -> runMatch(jobResultId, request, userId, sessionId)).toString();
        
        jobResult.setJobrunrJobId(jobrunrId);
        matchJobResultRepository.save(jobResult);

        return MatchJobAcceptedDto.builder()
                .jobId(jobrunrId)
                .pollUrl("/api/match/jobs/" + jobrunrId)
                .build();
    }

    public void runMatch(UUID matchJobResultId, MatchRequestDto request, UUID userId, String sessionId) {
        MatchJobResult jobResult = matchJobResultRepository.findById(matchJobResultId).orElseThrow();
        
        try {
            jobResult.setStatus(MatchJobResult.JobStatus.PROCESSING);
            matchJobResultRepository.save(jobResult);

            // 1. Resolve CV Text
            String cvText = null;
            if (request.getCvDocumentId() != null && userId != null) {
                CvDocument cvDoc = cvDocumentRepository.findByIdAndUserId(request.getCvDocumentId(), userId)
                        .orElseThrow(() -> new RuntimeException("CV Document not found"));
                cvText = cvDoc.getExtractedText();
            } else if (request.getCvText() != null && !request.getCvText().isBlank()) {
                cvText = request.getCvText();
            } else {
                throw new RuntimeException("CV input required");
            }

            // 2. Resolve JD Text
            String jdText = null;
            if (request.getJdUrl() != null && !request.getJdUrl().isBlank()) {
                jdText = jdScraperService.scrape(request.getJdUrl());
            } else if (request.getJdText() != null && !request.getJdText().isBlank()) {
                jdText = request.getJdText();
            } else {
                throw new RuntimeException("JD input required");
            }

            // 3. Save JD Snapshot
            JdSnapshot snapshot = JdSnapshot.builder()
                    .user(userId != null ? User.builder().id(userId).build() : null)
                    .sessionId(sessionId)
                    .sourceUrl(request.getJdUrl())
                    .companyName(request.getCompanyName())
                    .roleTitle(request.getRoleTitle())
                    .rawText(jdText)
                    .build();
            jdSnapshotRepository.save(snapshot);

            // 4. Score Match
            MatchScorerService.MatchResult scorerResult = matchScorerService.score(cvText, jdText);
            
            MatchResponseDto responseDto = MatchResponseDto.builder()
                    .matchResultId(snapshot.getId())
                    .status(scorerResult.getOverallScore() >= 65 ? "MATCH_SUCCESSFUL" : "BELOW_THRESHOLD")
                    .matchScore(scorerResult.getOverallScore())
                    .threshold(65)
                    .summary(scorerResult.getSummary())
                    .matchedSkills(scorerResult.getMatchedSkills())
                    .missingSkills(scorerResult.getMissingSkills())
                    .weakMatches(scorerResult.getWeakMatches().stream()
                            .map(wm -> MatchResponseDto.WeakMatchDto.builder().skill(wm.getSkill()).note(wm.getNote()).build())
                            .collect(Collectors.toList()))
                    .recommendation(scorerResult.getRecommendation())
                    .build();

            // 5. Threshold Check & Retailor
            if (responseDto.getMatchScore() >= 65) {
                String gapSummary = String.format("Missing: %s. Weak: %s", 
                        String.join(", ", scorerResult.getMissingSkills()),
                        scorerResult.getWeakMatches().stream().map(MatchScorerService.MatchResult.WeakMatch::getSkill).collect(Collectors.joining(", ")));
                
                CvRewriterService.RewriteResult rewriteResult = cvRewriterService.rewrite(cvText, jdText, gapSummary);
                PdfGeneratorService.PdfGenerationResult pdfResult = pdfGeneratorService.generate(rewriteResult);
                
                responseDto.setRetailoringOffered(true);
                responseDto.setRetailoredCv(MatchResponseDto.RetailoredCvDto.builder()
                        .downloadUrl(pdfResult.getDownloadUrl())
                        .expiresAt(pdfResult.getExpiresAt().toString())
                        .fileSizeKb(pdfResult.getFileSizeBytes() / 1024)
                        .changes(rewriteResult.getChanges())
                        .build());
            } else {
                responseDto.setRetailoringOffered(false);
            }

            // 6. Complete Job
            jobResult.setStatus(MatchJobResult.JobStatus.COMPLETED);
            jobResult.setResultJson(objectMapper.writeValueAsString(responseDto));
            jobResult.setCompletedAt(OffsetDateTime.now());
            matchJobResultRepository.save(jobResult);

        } catch (Exception e) {
            log.error("Match job failed", e);
            jobResult.setStatus(MatchJobResult.JobStatus.FAILED);
            jobResult.setErrorMessage(e.getMessage());
            jobResult.setCompletedAt(OffsetDateTime.now());
            matchJobResultRepository.save(jobResult);
            throw new RuntimeException(e);
        }
    }
}
