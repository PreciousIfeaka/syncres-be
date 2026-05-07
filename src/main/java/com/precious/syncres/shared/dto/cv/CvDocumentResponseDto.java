package com.precious.syncres.shared.dto.cv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.precious.syncres.entities.CvDocument;
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
public class CvDocumentResponseDto {
    private UUID id;
    private String originalFilename;
    private String storagePath;
    private String fileType;
    private Long fileSizeBytes;
    private String sessionId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String extractedText;
    private OffsetDateTime uploadedAt;

    public static CvDocumentResponseDto fromEntity(CvDocument cv) {
        return CvDocumentResponseDto.builder()
                .id(cv.getId())
                .sessionId(cv.getSessionId())
                .originalFilename(cv.getOriginalFilename())
                .storagePath(cv.getStoragePath())
                .fileType(cv.getFileType())
                .fileSizeBytes(cv.getFileSizeBytes())
                .uploadedAt(cv.getUploadedAt())
                .build();
    }
}
