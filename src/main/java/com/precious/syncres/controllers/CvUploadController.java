package com.precious.syncres.controllers;

import com.precious.syncres.entities.CvDocument;
import com.precious.syncres.repositories.CvDocumentRepository;
import com.precious.syncres.services.CvParserService;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import com.precious.syncres.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
@Slf4j
public class CvUploadController {

    private final CvParserService cvParserService;
    private final CvDocumentRepository cvDocumentRepository;
    private final S3Client s3Client;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @PostMapping("/upload")
    public ResponseEntity<CvDocument> upload(@RequestParam("file") MultipartFile file) {
        UUID userId = SecurityUtils.getCurrentUserId();
        
        String extractedText = cvParserService.parse(file);
        
        String fileId = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String storagePath = "user-cvs/" + userId + "/" + fileId;

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType(file.getContentType())
                    .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            CvDocument doc = CvDocument.builder()
                    .user(com.precious.syncres.entities.User.builder().id(userId).build())
                    .originalFilename(file.getOriginalFilename())
                    .storagePath(storagePath)
                    .fileType(getFileType(file.getOriginalFilename()))
                    .fileSizeBytes(file.getSize())
                    .extractedText(extractedText)
                    .build();

            return ResponseEntity.ok(cvDocumentRepository.save(doc));
        } catch (Exception e) {
            log.error("Failed to upload CV", e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<CvDocument>> list() {
        return ResponseEntity.ok(cvDocumentRepository.findAllByUserId(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CvDocument> get(@PathVariable UUID id) {
        return cvDocumentRepository.findByIdAndUserId(id, SecurityUtils.getCurrentUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        CvDocument doc = cvDocumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CV_NOT_FOUND, "CV not found"));

        // TODO: Check if CV is in use by applications if needed (v2 spec says check CV not in use)
        
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(doc.getStoragePath())
                    .build());
            cvDocumentRepository.delete(doc);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete CV from S3", e);
            throw new RuntimeException("Delete failed");
        }
    }

    private String getFileType(String filename) {
        if (filename == null) return "UNKNOWN";
        String ext = filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
        return ext.matches("PDF|DOCX|DOC") ? ext : "UNKNOWN";
    }
}
