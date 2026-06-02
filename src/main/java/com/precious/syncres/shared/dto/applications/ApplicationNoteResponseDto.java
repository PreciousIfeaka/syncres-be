package com.precious.syncres.shared.dto.applications;

import com.precious.syncres.entities.NoteType;
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
    private NoteType noteType;
    private OffsetDateTime createdAt;
}
