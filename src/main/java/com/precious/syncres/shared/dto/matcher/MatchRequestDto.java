package com.precious.syncres.shared.dto.matcher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRequestDto {
    private UUID cvDocumentId;
    private String cvText;
    private String jdUrl;
    private String jdText;
    private String companyName;
    private String roleTitle;
    private boolean saveAsApplication = false;
}
