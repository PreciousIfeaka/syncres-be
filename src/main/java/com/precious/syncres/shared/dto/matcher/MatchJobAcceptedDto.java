package com.precious.syncres.shared.dto.matcher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchJobAcceptedDto {
    private String jobId;
    private String pollUrl;
}
