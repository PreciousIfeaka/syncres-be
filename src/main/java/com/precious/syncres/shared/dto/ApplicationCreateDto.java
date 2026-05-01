package com.precious.syncres.shared.dto;

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
public class ApplicationCreateDto {
    private String companyName;
    private String roleTitle;
    private UUID cvDocumentId;
    private UUID jdSnapshotId;
    private String jdUrl;
    private OffsetDateTime appliedAt;
}
