package com.precious.syncres.shared.dto.matcher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultPollDto {
    private String jobId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private MatchResponseDto result;
    private String errorMessage;
}
