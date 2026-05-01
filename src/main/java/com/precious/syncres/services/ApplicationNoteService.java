package com.precious.syncres.services;

import com.precious.syncres.entities.ApplicationNote;
import com.precious.syncres.entities.JobApplication;
import com.precious.syncres.repositories.ApplicationRepository;
import com.precious.syncres.repositories.NoteRepository;
import com.precious.syncres.shared.dto.NoteCreateDto;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationNoteService {

    private final NoteRepository noteRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public void addNote(UUID id, NoteCreateDto dto, UUID userId) {
        JobApplication application = applicationRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found"));

        ApplicationNote note = ApplicationNote.builder()
                .application(application)
                .content(dto.getContent())
                .noteType(dto.getNoteType() != null ? dto.getNoteType() : "GENERAL")
                .build();
        
        noteRepository.save(note);
    }

    @Transactional
    public void deleteNote(UUID id, UUID noteId, UUID userId) {
        // Verify application ownership first
        if (!applicationRepository.existsByIdAndUserIdAndDeletedAtIsNull(id, userId)) {
            throw new AppException(ErrorCode.APPLICATION_NOT_FOUND, "Application not found");
        }

        ApplicationNote note = noteRepository.findByIdAndApplicationId(noteId, id)
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "Note not found"));

        noteRepository.delete(note);
    }
}
