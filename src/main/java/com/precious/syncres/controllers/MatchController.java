package com.precious.syncres.controllers;

import com.precious.syncres.repositories.MatchJobResultRepository;
import com.precious.syncres.services.MatchJobService;
import com.precious.syncres.shared.dto.cv.DownloadFileResponseDto;
import com.precious.syncres.shared.dto.matcher.MatchJobAcceptedDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precious.syncres.shared.dto.matcher.MatchRequestDto;
import com.precious.syncres.shared.dto.matcher.MatchResultPollDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Controller for AI-driven CV matching and retailoring.
 * Supports both anonymous (session-based) and authenticated (JWT-based) access.
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CV Matching", description = "Endpoints for AI-powered CV-JD matching and document retailoring")
public class MatchController {

    private final MatchJobService matchJobService;
    private final MatchJobResultRepository matchJobResultRepository;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @Operation(summary = "Submit a match job", description = "Enqueues a background job to match a CV with a JD. Accessible to both anonymous and registered users.")
    @PostMapping
    public ResponseEntity<MatchJobAcceptedDto> match(@RequestBody MatchRequestDto request, HttpSession session) {
        return ResponseEntity.accepted().body(matchJobService.enqueueMatch(request, session));
    }

    @Operation(summary = "Poll match result", description = "Retrieves the status and result of a previously submitted match job.")
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<MatchResultPollDto> pollResult(
            @Parameter(description = "The JobRunr ID of the matching job") @PathVariable String jobId, 
            HttpSession session) {
        return ResponseEntity.ok(matchJobService.pollMatchResult(jobId, session));
    }

    @Operation(summary = "Download file", description = "Streams a file from S3 (original CV or retailored). Requires authentication and a valid HMAC signature.")
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "The S3 key of the file") @RequestParam String key,
            @Parameter(description = "Epoch seconds when the link expires") @RequestParam long expires,
            @Parameter(description = "HMAC-SHA256 signature") @RequestParam String sig) {

        DownloadFileResponseDto response = matchJobService.downloadFile(key, expires, sig);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + response.getFilename() + "\"")
                .contentType(response.getFilename().endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(response.getFileStream()));
    }
}
