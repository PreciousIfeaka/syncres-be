package com.precious.syncres.shared.dto.applications;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.ApplicationStatusHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApplicationStatusHistoryResponseDto {
    private UUID id;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;
    private String note;
    private OffsetDateTime changedAt;

    public static ApplicationStatusHistoryResponseDto toDto(ApplicationStatusHistory h) {
        return ApplicationStatusHistoryResponseDto.builder()
                .id(h.getId())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .changedAt(h.getChangedAt())
                .note(h.getNote())
                .build();
    }
}
