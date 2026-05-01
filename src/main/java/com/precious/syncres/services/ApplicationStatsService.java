package com.precious.syncres.services;

import com.precious.syncres.repositories.ApplicationRepository;
import com.precious.syncres.shared.dto.StatsResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationStatsService {

    private final ApplicationRepository applicationRepository;

    public StatsResponseDto getStats(UUID userId) {
        long total = applicationRepository.countByUserIdAndDeletedAtIsNull(userId);
        
        Map<String, Long> breakdown = new HashMap<>();
        applicationRepository.countByStatusForUser(userId).forEach(m -> {
            breakdown.put(m.get("status").toString(), (Long) m.get("count"));
        });

        Double avgScore = applicationRepository.getAverageMatchScoreForUser(userId);
        
        // v2: Last 30 days count
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long last30Days = applicationRepository.countByUserIdAndCreatedAtAfterAndDeletedAtIsNull(userId, thirtyDaysAgo);

        return StatsResponseDto.builder()
                .totalApplications(total)
                .statusBreakdown(breakdown)
                .averageMatchScore(avgScore)
                .applicationsLast30Days(last30Days)
                .build();
    }
}
