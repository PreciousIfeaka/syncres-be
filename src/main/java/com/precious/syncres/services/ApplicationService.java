package com.precious.syncres.services;

import com.precious.syncres.entities.*;
import com.precious.syncres.repositories.*;
import com.precious.syncres.shared.dto.ApplicationCreateDto;
import com.precious.syncres.shared.dto.ApplicationResponseDto;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final CvDocumentRepository cvDocumentRepository;
    private final JdSnapshotRepository jdSnapshotRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional
    public ApplicationResponseDto createApplication(ApplicationCreateDto dto, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        
        JobApplication application = JobApplication.builder()
                .user(user)
                .companyName(dto.getCompanyName())
                .roleTitle(dto.getRoleTitle())
                .jdUrl(dto.getJdUrl())
                .applicationStatus(ApplicationStatus.SAVED)
                .appliedAt(dto.getAppliedAt() != null ? dto.getAppliedAt() : OffsetDateTime.now())
                .build();

        if (dto.getCvDocumentId() != null) {
            if (!cvDocumentRepository.existsByIdAndUserId(dto.getCvDocumentId(), userId)) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "CV document does not belong to user");
            }
            application.setCvDocument(CvDocument.builder().id(dto.getCvDocumentId()).build());
        }

        if (dto.getJdSnapshotId() != null) {
            // Check JD snapshot ownership (v2 requirement)
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
                .toStatus(ApplicationStatus.SAVED)
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

    private ApplicationResponseDto mapToResponse(JobApplication app) {
        return ApplicationResponseDto.builder()
                .id(app.getId())
                .companyName(app.getCompanyName())
                .roleTitle(app.getRoleTitle())
                .status(app.getApplicationStatus())
                .matchScore(app.getMatchScore())
                .createdAt(app.getCreatedAt())
                .appliedAt(app.getAppliedAt())
                .build();
    }
}
