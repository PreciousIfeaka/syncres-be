package com.precious.syncres.controllers;

import com.precious.syncres.entities.MatchJobResult;
import com.precious.syncres.repositories.MatchJobResultRepository;
import com.precious.syncres.services.MatchJobService;
import com.precious.syncres.shared.dto.*;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import com.precious.syncres.shared.util.HmacUtils;
import com.precious.syncres.shared.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@Slf4j
public class MatchController {

    private final MatchJobService matchJobService;
    private final MatchJobResultRepository matchJobResultRepository;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @PostMapping
    public ResponseEntity<MatchJobAcceptedDto> match(@RequestBody MatchRequestDto request, HttpSession session) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String sessionId = userId == null ? session.getId() : null;
        
        return ResponseEntity.accepted().body(matchJobService.enqueueMatch(request, userId, sessionId));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<MatchResultPollDto> pollResult(@PathVariable String jobId, HttpSession session) {
        MatchJobResult jobResult = matchJobResultRepository.findByJobrunrJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "Job not found"));

        UUID currentUserId = SecurityUtils.getCurrentUserId();
        
        // Access Control logic
        if (jobResult.getUser() != null) {
            if (currentUserId == null || !jobResult.getUser().getId().equals(currentUserId)) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "You do not have access to this job result");
            }
        } else {
            // Anonymous job
            if (!session.getId().equals(jobResult.getSessionId())) {
                throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "Access denied to anonymous job result");
            }
        }

        MatchResultPollDto pollDto = MatchResultPollDto.builder()
                .jobId(jobId)
                .status(jobResult.getStatus().name())
                .errorMessage(jobResult.getErrorMessage())
                .build();

        if (jobResult.getStatus() == MatchJobResult.JobStatus.COMPLETED && jobResult.getResultJson() != null) {
            try {
                pollDto.setResult(objectMapper.readValue(jobResult.getResultJson(), MatchResponseDto.class));
            } catch (Exception e) {
                log.error("Failed to parse job result JSON", e);
                throw new RuntimeException("Internal error parsing result");
            }
        }

        return ResponseEntity.ok(pollDto);
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadCv(
            @PathVariable String fileId,
            @RequestParam long expires,
            @RequestParam String sig) {
        
        // 1. Authentication check (v2: requires authentication)
        if (SecurityUtils.getCurrentUserId() == null) {
            throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "Authentication required for downloads");
        }

        // 2. Expiry check
        if (Instant.now().getEpochSecond() > expires) {
            throw new AppException(ErrorCode.DOWNLOAD_EXPIRED, "Download link has expired");
        }

        // 3. Signature verification
        String dataToVerify = fileId + ":" + expires;
        if (!HmacUtils.verifyHmac(dataToVerify, sig, hmacSecret)) {
            throw new AppException(ErrorCode.DOWNLOAD_INVALID_SIGNATURE, "Invalid download signature");
        }

        // 4. Stream from S3
        try {
            String storageKey = "retailored-cvs/" + fileId;
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileId + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(s3Stream));
        } catch (Exception e) {
            log.error("Failed to retrieve file from S3", e);
            throw new AppException(ErrorCode.CV_NOT_FOUND, "File not found");
        }
    }
}
