package com.precious.syncres.shared.dto.applications;

import com.precious.syncres.entities.ApplicationNote;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteCreateDto {
    @NotBlank
    private String content;
    private ApplicationNote.NoteType noteType;
}
