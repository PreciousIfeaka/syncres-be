package com.precious.syncres.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponseDto {
    private long totalApplications;
    private Map<String, Long> statusBreakdown;
    private Double averageMatchScore;
    private long applicationsLast30Days;
}
