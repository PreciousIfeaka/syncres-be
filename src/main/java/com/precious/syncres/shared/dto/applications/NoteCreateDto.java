package com.precious.syncres.shared.dto.applications;

import com.precious.syncres.entities.ApplicationNote;
import com.precious.syncres.entities.NoteType;
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
    private NoteType noteType;
}
