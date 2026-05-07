package com.precious.syncres.shared.dto.applications;

import com.precious.syncres.entities.ApplicationNote;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationNoteResponseDto {
    private UUID id;
    private String content;
    private ApplicationNote.NoteType noteType;
    private OffsetDateTime createdAt;
}
