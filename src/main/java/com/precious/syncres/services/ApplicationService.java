package com.precious.syncres.services;

import com.precious.syncres.entities.*;
import com.precious.syncres.repositories.*;
import com.precious.syncres.shared.dto.applications.*;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.precious.syncres.repositories.specifications.JobApplicationSpecification.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final CvDocumentRepository cvDocumentRepository;
    private final JdSnapshotRepository jdSnapshotRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public ApplicationResponseDto createApplication(ApplicationCreateDto dto, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        
        if (dto.getCvDocumentId() == null) {
            throw new AppException(ErrorCode.CV_INPUT_REQUIRED, "Every application must have a CV linked.");
        }
        
        JobApplication application = JobApplication.builder()
                .user(user)
                .companyName(dto.getCompanyName())
                .roleTitle(dto.getRoleTitle())
                .jdUrl(dto.getJdUrl())
                .applicationStatus(dto.getCurrentStatus() == null ? ApplicationStatus.SAVED : dto.getCurrentStatus())
                .appliedAt(dto.getAppliedAt() != null ? dto.getAppliedAt() : OffsetDateTime.now())
                .build();

        if (!cvDocumentRepository.existsByIdAndUserId(dto.getCvDocumentId(), userId)) {
            throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "CV document does not belong to user");
        }
        application.setCvDocument(CvDocument.builder().id(dto.getCvDocumentId()).build());

        if (dto.getJdSnapshotId() != null) {
            JdSnapshot snapshot = jdSnapshotRepository.findById(dto.getJdSnapshotId())
                    .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "JD snapshot not found"));
            if (snapshot.getUser() != null && !snapshot.getUser().getId().equals(userId)) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "JD snapshot does not belong to user");
            }
            application.setJdSnapshot(snapshot);
        }

        application = applicationRepository.save(application);

        statusHistoryRepository.save(ApplicationStatusHistory.builder()
                .application(application)
                .toStatus(dto.getCurrentStatus() == null ? ApplicationStatus.SAVED : dto.getCurrentStatus())
                .note("Application created")
                .build());

        return mapToResponse(application);
    }

    @Transactional
    public void deleteApplication(UUID id, UUID userId) {
        JobApplication application = applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));
        
        application.setDeletedAt(OffsetDateTime.now());
        applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponseDto> listApplications(UUID userId, ApplicationStatus status, String company) {
        return applicationRepository.findAll(
                        Specification
                                .where(forUser(userId))
                                .and(withCvDocument())
                                .and(notDeleted())
                                .and(withStatus(status))
                                .and(companyLike(company)),
                        Pageable.unpaged()
                )
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationResponseDto getApplication(UUID id, UUID userId) {
        JobApplication application = applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));
        return mapToResponse(application);
    }

    public StatsResponseDto getStats(UUID userId) {
        long total = applicationRepository.countByUserIdAndDeletedAtIsNull(userId);

        Map<String, Long> breakdown = new HashMap<>();
        applicationRepository.countByStatusForUser(userId).forEach(m -> {
            breakdown.put(m.get("status").toString(), (Long) m.get("count"));
        });

        Double avgScore = applicationRepository.getAverageMatchScoreForUser(userId);

        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long last30Days = applicationRepository.countByUserIdAndCreatedAtAfterAndDeletedAtIsNull(userId, thirtyDaysAgo);

        return StatsResponseDto.builder()
                .totalApplications(total)
                .statusBreakdown(breakdown)
                .averageMatchScore(avgScore)
                .applicationsLast30Days(last30Days)
                .build();
    }

    @Transactional
    public ApplicationResponseDto updateStatus(UUID id, StatusUpdateDto dto, UUID userId) {
        JobApplication application = applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));

        ApplicationStatus fromStatus = application.getApplicationStatus();
        ApplicationStatus toStatus = dto.getStatus();

        if (fromStatus.isTerminal()) {
            throw new AppException(ErrorCode.STATUS_IS_TERMINAL, "Cannot transition from terminal status " + fromStatus);
        }

        if (!fromStatus.allowedTransitions().contains(toStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Invalid transition to " + toStatus + ". Allowed: " + fromStatus.allowedTransitions());
        }

        application.setApplicationStatus(toStatus);
        if (toStatus == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(OffsetDateTime.now());
        }
        applicationRepository.save(application);

        statusHistoryRepository.save(ApplicationStatusHistory.builder()
                .application(application)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(dto.getNote())
                .build());

        return mapToResponse(application);
    }

    public List<ApplicationStatusHistoryResponseDto> getStatusHistory(UUID id) {
        return statusHistoryRepository.findAllByApplicationIdOrderByChangedAtDesc(id)
                .stream().map(ApplicationStatusHistoryResponseDto::toDto).toList();
    }

    @Transactional
    public ApplicationNoteResponseDto addNote(UUID id, NoteCreateDto dto, UUID userId) {
        JobApplication application = applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));

        ApplicationNote note = ApplicationNote.builder()
                .application(application)
                .content(dto.getContent())
                .noteType(dto.getNoteType())
                .build();

        noteRepository.save(note);

        return ApplicationNoteResponseDto.builder()
                .id(note.getId())
                .content(note.getContent())
                .noteType(note.getNoteType())
                .createdAt(note.getCreatedAt())
                .build();
    }

    public List<ApplicationNoteResponseDto> listApplicationNotes(UUID applicationId) {
        return noteRepository.findAllByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .map(note -> new ApplicationNoteResponseDto(
                                        note.getId(), note.getContent(), note.getNoteType(), note.getCreatedAt()
                                        )
                )
                .toList();
    }

    @Transactional
    public void deleteNote(UUID id, UUID noteId, UUID userId) {
        if (!applicationRepository.existsByIdAndUserIdAndDeletedAtIsNull(id, userId)) {
            throw new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found");
        }

        ApplicationNote note = noteRepository.findByIdAndApplicationId(noteId, id)
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "Note not found"));

        noteRepository.delete(note);
    }

    public ApplicationResponseDto mapToResponse(JobApplication app) {
        String downloadUrl = null;
        if (app.getRetailoredCvPath() != null) {
            downloadUrl = fileStorageService.generateSignedUrl(app.getRetailoredCvPath());
        } else if (app.getCvDocument() != null) {
            downloadUrl = fileStorageService.generateSignedUrl(app.getCvDocument().getStoragePath());
        }

        return ApplicationResponseDto.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .roleTitle(app.getRoleTitle())
                .status(app.getApplicationStatus())
                .matchScore(app.getMatchScore() != null ? app.getMatchScore() : null)
                .createdAt(app.getCreatedAt())
                .appliedAt(app.getAppliedAt())
                .cvDownloadUrl(downloadUrl)
                .jdUrl(app.getJdUrl())
                .jdText(app.getJdSnapshot() != null ? app.getJdSnapshot().getRawText() : null)
                .build();
    }
}
