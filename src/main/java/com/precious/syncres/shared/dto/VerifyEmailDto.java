package com.precious.syncres.shared.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 6)
    private String otp;
}
