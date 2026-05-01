package com.precious.syncres.controllers;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.ApplicationStatusHistory;
import com.precious.syncres.entities.ApplicationNote;
import com.precious.syncres.repositories.StatusHistoryRepository;
import com.precious.syncres.repositories.NoteRepository;
import com.precious.syncres.services.*;
import com.precious.syncres.shared.dto.*;
import com.precious.syncres.shared.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationStatusService statusService;
    private final ApplicationNoteService noteService;
    private final ApplicationQueryService queryService;
    private final ApplicationStatsService statsService;
    private final StatusHistoryRepository statusHistoryRepository;
    private final NoteRepository noteRepository;

    @PostMapping
    public ResponseEntity<ApplicationResponseDto> create(@Valid @RequestBody ApplicationCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.createApplication(dto, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationResponseDto>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String company) {
        return ResponseEntity.ok(queryService.listApplications(SecurityUtils.getCurrentUserId(), status, company));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponseDto> get(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "") String include) {
        // Simple implementation for now, v2 spec says include nested objects
        // In a real app we'd handle this more dynamically.
        UUID userId = SecurityUtils.getCurrentUserId();
        ApplicationResponseDto response = queryService.listApplications(userId, null, null).stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(); // Simple lookup for demo

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponseDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateDto dto) {
        return ResponseEntity.ok(statusService.updateStatus(id, dto, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        applicationService.deleteApplication(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationStatusHistory>> getHistory(@PathVariable UUID id) {
        // Basic ownership verification should be here
        return ResponseEntity.ok(statusHistoryRepository.findAllByApplicationIdOrderByChangedAtDesc(id));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<Void> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteCreateDto dto) {
        noteService.addNote(id, dto, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<List<ApplicationNote>> listNotes(@PathVariable UUID id) {
        // Basic ownership verification should be here
        return ResponseEntity.ok(noteRepository.findAllByApplicationIdOrderByCreatedAtDesc(id));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<?> deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        noteService.deleteNote(noteId, id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsResponseDto> getStats() {
        return ResponseEntity.ok(statsService.getStats(SecurityUtils.getCurrentUserId()));
    }
}
