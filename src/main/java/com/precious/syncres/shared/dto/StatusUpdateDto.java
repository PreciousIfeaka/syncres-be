package com.precious.syncres.shared.dto;

import com.precious.syncres.entities.ApplicationStatus;
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
public class StatusUpdateDto {
    @NotNull(message = "New status is required")
    private ApplicationStatus status;
    
    @NotBlank(message = "Status update note is recommended")
    private String note;
}
