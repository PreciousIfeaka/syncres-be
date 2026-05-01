package com.precious.syncres.services;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.ApplicationStatusHistory;
import com.precious.syncres.entities.JobApplication;
import com.precious.syncres.repositories.ApplicationRepository;
import com.precious.syncres.repositories.StatusHistoryRepository;
import com.precious.syncres.shared.dto.ApplicationResponseDto;
import com.precious.syncres.shared.dto.StatusUpdateDto;
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
public class ApplicationStatusService {

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;

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
