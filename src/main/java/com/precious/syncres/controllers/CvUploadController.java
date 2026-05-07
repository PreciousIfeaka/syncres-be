package com.precious.syncres.controllers;

import com.precious.syncres.services.CvUploadService;
import com.precious.syncres.shared.dto.cv.CvDocumentResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Controller for managing CV document uploads and storage.
 * Requires user authentication.
 */
@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CV Management", description = "Endpoints for uploading, listing, and deleting CV documents")
public class CvUploadController {

    private final CvUploadService cvUploadService;

    @Operation(summary = "Upload a CV", description = "Parses and stores a CV (PDF/DOCX) in S3. Accessible to both anonymous (session-based) and registered users. Automatically extracts text for future matching.")
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CvDocumentResponseDto> upload(@RequestParam("file") MultipartFile file, jakarta.servlet.http.HttpSession session) {
        return ResponseEntity.ok(cvUploadService.uploadCV(file, session));
    }

    @Operation(summary = "List all CVs", description = "Returns a list of all CV documents uploaded by the current user.")
    @GetMapping
    public ResponseEntity<List<CvDocumentResponseDto>> list() {
        return ResponseEntity.ok(cvUploadService.listUserCvs());
    }

    @Operation(summary = "Get CV metadata", description = "Retrieves metadata for a specific CV document.")
    @GetMapping("/{id}")
    public ResponseEntity<CvDocumentResponseDto> get(
            @Parameter(description = "The UUID of the CV document") @PathVariable UUID id) {
        return ResponseEntity.ok(cvUploadService.getCvDocument(id));
    }

    @Operation(summary = "Delete a CV", description = "Removes the CV from S3 and the database.")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "The UUID of the CV document to delete") @PathVariable UUID id) {
        cvUploadService.deleteCV(id);

        return ResponseEntity.status(204).build();
    }
}
