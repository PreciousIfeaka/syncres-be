package com.precious.syncres.controllers;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.ApplicationStatusHistory;
import com.precious.syncres.repositories.StatusHistoryRepository;
import com.precious.syncres.repositories.NoteRepository;
import com.precious.syncres.services.*;
import com.precious.syncres.shared.dto.applications.*;
import com.precious.syncres.shared.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for managing job applications and their lifecycle.
 * Requires user authentication.
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Job Applications", description = "Endpoints for tracking job applications, status history, and notes")
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "Create an application", description = "Manually creates a job application record.")
    @PostMapping
    public ResponseEntity<ApplicationResponseDto> create(@Valid @RequestBody ApplicationCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.createApplication(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "List applications", description = "Retrieves a filtered list of applications for the current user.")
    @GetMapping
    public ResponseEntity<List<ApplicationResponseDto>> list(
            @Parameter(description = "Filter by application status") @RequestParam(required = false) ApplicationStatus status,
            @Parameter(description = "Filter by company name") @RequestParam(required = false) String company) {
        return ResponseEntity.ok(applicationService.listApplications(SecurityUtils.getCurrentUserId(), status, company));
    }


    @Operation(summary = "Get application details", description = "Retrieves a single application by ID.")
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponseDto> get(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getApplication(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Update application status", description = "Transitions an application to a new status and records history.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponseDto> updateStatus(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateDto dto) {
        return ResponseEntity.ok(applicationService.updateStatus(id, dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Soft-delete an application", description = "Marks an application as deleted without physical removal.")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "The UUID of the application to delete") @PathVariable UUID id) {
        applicationService.deleteApplication(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get status history", description = "Returns the audit trail of status changes for an application.")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationStatusHistoryResponseDto>> getHistory(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getStatusHistory(id));
    }

    @Operation(summary = "Add a note", description = "Appends a new note (e.g., interview prep) to an application.")
    @PostMapping("/{id}/notes")
    public ResponseEntity<ApplicationNoteResponseDto> addNote(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id,
            @Valid @RequestBody NoteCreateDto dto) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                applicationService.addNote(id, dto, SecurityUtils.getCurrentUserId())
        );
    }

    @Operation(summary = "List application notes", description = "Returns all notes associated with an application.")
    @GetMapping("/{id}/notes")
    public ResponseEntity<List<ApplicationNoteResponseDto>> listNotes(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.listApplicationNotes(id));
    }

    @Operation(summary = "Delete a note", description = "Permanently deletes a specific application note.")
    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<?> deleteNote(
            @Parameter(description = "The UUID of the application") @PathVariable UUID id,
            @Parameter(description = "The UUID of the note to delete") @PathVariable UUID noteId) {
        applicationService.deleteNote(id, noteId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get application statistics", description = "Returns aggregate metrics (totals, status breakdown, 30-day activity).")
    @GetMapping("/stats")
    public ResponseEntity<StatsResponseDto> getStats() {
        return ResponseEntity.ok(applicationService.getStats(SecurityUtils.getCurrentUserId()));
    }
}
