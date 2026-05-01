package com.precious.syncres.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponseDto {
    private UUID matchResultId;
    private String status; // BELOW_THRESHOLD, MATCH_SUCCESSFUL
    private int matchScore;
    private int threshold;
    private String summary;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<WeakMatchDto> weakMatches;
    private String recommendation;
    private boolean retailoringOffered;
    private RetailoredCvDto retailoredCv;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeakMatchDto {
        private String skill;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetailoredCvDto {
        private String downloadUrl;
        private String expiresAt;
        private Long fileSizeKb;
        private List<String> changes;
    }
}
