package com.precious.syncres.shared.dto;

import com.precious.syncres.entities.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponseDto {
    private UUID id;
    private String companyName;
    private String roleTitle;
    private ApplicationStatus status;
    private Integer matchScore;
    private OffsetDateTime createdAt;
    private OffsetDateTime appliedAt;
}
