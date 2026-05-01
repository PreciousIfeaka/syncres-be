package com.precious.syncres.services;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.repositories.ApplicationRepository;
import com.precious.syncres.shared.dto.ApplicationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationQueryService {

    private final ApplicationRepository applicationRepository;

    public List<ApplicationResponseDto> listApplications(UUID userId, ApplicationStatus status, String company) {
        return applicationRepository.findAllFiltered(userId, status, company, Pageable.unpaged())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ApplicationResponseDto mapToResponse(com.precious.syncres.entities.JobApplication app) {
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
