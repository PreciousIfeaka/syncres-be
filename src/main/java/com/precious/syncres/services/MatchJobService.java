package com.precious.syncres.services;

import com.precious.syncres.entities.*;
import com.precious.syncres.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precious.syncres.shared.dto.cv.DownloadFileResponseDto;
import com.precious.syncres.shared.dto.matcher.MatchJobAcceptedDto;
import com.precious.syncres.shared.dto.matcher.MatchRequestDto;
import com.precious.syncres.shared.dto.matcher.MatchResponseDto;
import com.precious.syncres.shared.dto.matcher.MatchResultPollDto;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import com.precious.syncres.shared.util.SecurityUtils;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

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
    private final ApplicationRepository applicationRepository;
    private final JdScraperService jdScraperService;
    private final MatchScorerService matchScorerService;
    private final CvRewriterService cvRewriterService;
    private final PdfGeneratorService pdfGeneratorService;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    @Transactional
    public MatchJobAcceptedDto enqueueMatch(MatchRequestDto request, HttpSession session) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String sessionId = userId == null ? session.getId() : "";

        MatchJobResult jobResult = MatchJobResult.builder()
                .jobrunrJobId("PENDING")
                .user(userId != null ? User.builder().id(userId).build() : null)
                .sessionId(sessionId)
                .status(MatchJobResult.JobStatus.PENDING)
                .build();
        
        jobResult = matchJobResultRepository.save(jobResult);
        final UUID jobResultId = jobResult.getId();

        String jobrunrId = jobScheduler.enqueue(() -> runMatch(jobResultId, request)).toString();
        
        jobResult.setJobrunrJobId(jobrunrId);
        matchJobResultRepository.save(jobResult);

        return MatchJobAcceptedDto.builder()
                .jobId(jobrunrId)
                .pollUrl("/api/match/jobs/" + jobrunrId)
                .build();
    }

    public void runMatch(UUID matchJobResultId, MatchRequestDto request) {
        MatchJobResult jobResult = matchJobResultRepository.findById(matchJobResultId).orElseThrow();
        UUID userId = jobResult.getUser() != null ? jobResult.getUser().getId() : null;
        String sessionId = jobResult.getSessionId() != null ? jobResult.getSessionId() : "";
        
        try {
            jobResult.setStatus(MatchJobResult.JobStatus.PROCESSING);
            matchJobResultRepository.save(jobResult);

            // 1. Resolve CV Text
            String cvText = null;
            CvDocument originalCv = null;
            if (request.getCvDocumentId() != null) {
                if (userId != null) {
                    originalCv = cvDocumentRepository.findByIdAndUserId(request.getCvDocumentId(), userId)
                            .orElseThrow(() -> new RuntimeException("CV Document not found"));
                } else {
                    originalCv = cvDocumentRepository.findByIdAndSessionId(request.getCvDocumentId(), sessionId)
                            .orElseThrow(() -> new RuntimeException("CV Document not found for session"));
                }
                cvText = originalCv.getExtractedText();
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

            // 3. Score Match
            MatchScorerService.MatchResult scorerResult = matchScorerService.score(cvText, jdText);

            // 4. Save JD Snapshot
            JdSnapshot snapshot = JdSnapshot.builder()
                    .user(userId != null ? User.builder().id(userId).build() : null)
                    .sessionId(sessionId.isEmpty() ? null : sessionId)
                    .sourceUrl(request.getJdUrl())
                    .companyName(!request.getCompanyName().isBlank() ? request.getCompanyName() : scorerResult.getCompanyName())
                    .roleTitle(!request.getRoleTitle().isEmpty() ? request.getRoleTitle() : scorerResult.getRoleTitle())
                    .rawText(jdText)
                    .build();
            jdSnapshotRepository.save(snapshot);
            
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
            String retailoredPath = null;
            if (responseDto.getMatchScore() >= 85) {
                // EXCELLENT MATCH - Skip retailoring
                responseDto.setRetailoringOffered(false);
                responseDto.setRecommendation("Your CV is an excellent match! No retailoring required.");
            } else if (responseDto.getMatchScore() >= 65) {
                // GOOD MATCH - Retailor
                String gapSummary = String.format("Missing: %s. Weak: %s", 
                        String.join(", ", scorerResult.getMissingSkills()),
                        scorerResult.getWeakMatches().stream().map(MatchScorerService.MatchResult.WeakMatch::getSkill).collect(Collectors.joining(", ")));
                
                CvRewriterService.RewriteResult rewriteResult = cvRewriterService.rewrite(cvText, jdText, gapSummary);
                PdfGeneratorService.PdfGenerationResult pdfResult = pdfGeneratorService.generate(rewriteResult);
                
                retailoredPath = pdfResult.getStoragePath();
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

            // Optional: Save as application if requested and authenticated
            if (request.isSaveAsApplication() && userId != null) {
                JobApplication application = JobApplication.builder()
                        .user(User.builder().id(userId).build())
                        .companyName(snapshot.getCompanyName())
                        .roleTitle(snapshot.getRoleTitle())
                        .applicationStatus(ApplicationStatus.SAVED)
                        .cvDocument(originalCv)
                        .jdSnapshot(snapshot)
                        .jdUrl(request.getJdUrl())
                        .matchScore(responseDto.getMatchScore())
                        .matchSummary(responseDto.getSummary())
                        .retailoredCvPath(retailoredPath)
                        .appliedAt(OffsetDateTime.now())
                        .build();
                applicationRepository.save(application);
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

    public MatchResultPollDto pollMatchResult(String jobId, HttpSession session) {
        MatchJobResult jobResult = matchJobResultRepository.findByJobrunrJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "Job not found"));

        UUID currentUserId = SecurityUtils.getCurrentUserId();

        // Access Control logic
        if (jobResult.getUser() != null) {
            if (!jobResult.getUser().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "You do not have access to this job result");
            }
        } else {
            // Anonymous job
            if (!session.getId().equals(jobResult.getSessionId())) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "Access denied to anonymous job result");
            }
        }

        String rawError = jobResult.getErrorMessage();
        String displayError = (rawError != null && rawError.length() < 25)
                ? rawError
                : (jobResult.getStatus() == MatchJobResult.JobStatus.FAILED ? "Internal error parsing result" : null);
        MatchResultPollDto pollDto = MatchResultPollDto.builder()
                .jobId(jobId)
                .status(jobResult.getStatus().name())
                .errorMessage(displayError)
                .build();

        if (jobResult.getStatus() == MatchJobResult.JobStatus.COMPLETED && jobResult.getResultJson() != null) {
            try {
                pollDto.setResult(objectMapper.readValue(jobResult.getResultJson(), MatchResponseDto.class));
            } catch (Exception e) {
                log.error("Failed to parse job result JSON", e);
                throw new RuntimeException("Internal error parsing result");
            }
        }

        return pollDto;
    }

    public DownloadFileResponseDto downloadFile(String key, Long expires, String sig) {
        // 1. Authentication check
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "Authentication required for downloads");
        }

        // 2. Signature and Expiry check
        try {
            fileStorageService.validateSignature(key, expires, sig);
        } catch (Exception e) {
            throw new AppException(ErrorCode.DOWNLOAD_INVALID_SIGNATURE, e.getMessage());
        }

        // 3. Stream from S3
        try {
            ResponseInputStream<GetObjectResponse> s3Stream = fileStorageService.downloadFile(key);
            String filename = key.substring(key.lastIndexOf("/") + 1);

            return DownloadFileResponseDto.builder()
                    .filename(filename)
                    .fileStream(s3Stream)
                    .build();
        } catch (Exception e) {
            log.error("Failed to retrieve file from S3", e);
            throw new AppException(ErrorCode.CV_NOT_FOUND, "File not found");
        }
    }
}
