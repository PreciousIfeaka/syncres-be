package com.precious.syncres.shared.dto;

import com.precious.syncres.entities.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteCreateDto {
    @NotBlank(message = "Note content cannot be empty")
    private String content;
    
    @NotNull(message = "Note type is required")
    private NoteType noteType;
}
