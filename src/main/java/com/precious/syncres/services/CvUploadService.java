package com.precious.syncres.services;

import com.precious.syncres.entities.CvDocument;
import com.precious.syncres.entities.User;
import com.precious.syncres.repositories.CvDocumentRepository;
import com.precious.syncres.shared.dto.cv.CvDocumentResponseDto;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import com.precious.syncres.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CvUploadService {
    private final CvDocumentRepository cvDocumentRepository;
    private final S3Client s3Client;
    private final CvParserService cvParserService;

    @Value("${s3.bucket-name}")
    private String bucketName;

    public CvDocumentResponseDto uploadCV(MultipartFile file, jakarta.servlet.http.HttpSession session) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String sessionId = userId == null ? session.getId() : null;

        String extractedText = cvParserService.parse(file);

        String fileId = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String storagePath = userId != null 
                ? "user-cvs/" + userId + "/" + fileId 
                : "session-cvs/" + sessionId + "/" + fileId;

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(file.getContentType())
                    .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            CvDocument doc = CvDocument.builder()
                    .user(userId != null ? User.builder().id(userId).build() : null)
                    .sessionId(sessionId)
                    .originalFilename(file.getOriginalFilename())
                    .storagePath(storagePath)
                    .fileType(getFileType(file.getOriginalFilename()))
                    .fileSizeBytes(file.getSize())
                    .extractedText(extractedText)
                    .build();

            return CvDocumentResponseDto.fromEntity(
                    cvDocumentRepository.save(doc)
            );
        } catch(Exception e) {
            log.error("Failed to upload CV", e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }

    public void deleteCV(UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        CvDocument doc = cvDocumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CV_NOT_FOUND, "CV not found"));

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(doc.getStoragePath())
                    .build());
            cvDocumentRepository.delete(doc);
        } catch (Exception e) {
            log.error("Failed to delete CV from S3", e);
            throw new RuntimeException("Delete failed");
        }
    }

    public List<CvDocumentResponseDto> listUserCvs() {
        return cvDocumentRepository.findAllByUserId(SecurityUtils.getCurrentUserId()).stream().map(
                CvDocumentResponseDto::fromEntity
        ).toList();
    }

    public CvDocumentResponseDto getCvDocument(UUID id) {
        CvDocument cv = cvDocumentRepository.findByIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.CV_NOT_FOUND, "CV not found"));
        CvDocumentResponseDto dto = CvDocumentResponseDto.fromEntity(cv);
        dto.setExtractedText(cv.getExtractedText());
        return dto;
    }

    private String getFileType(String filename) {
        if (filename == null) return "UNKNOWN";
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
        return ext.matches("PDF|DOCX|DOC") ? ext : "UNKNOWN";
    }
}
