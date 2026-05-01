package com.precious.syncres.controllers;

import com.precious.syncres.services.AuthService;
import com.precious.syncres.shared.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for user authentication and lifecycle management.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and password management")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user", description = "Initiates user registration and triggers an async email verification OTP.")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDto dto) {
        authService.register(dto);
        return ResponseEntity.accepted().body(Map.of("message", "Verification OTP sent to your email"));
    }

    @Operation(summary = "Verify user email", description = "Verifies the email using the 6-digit OTP and returns a JWT on success.")
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponseDto> verifyEmail(@Valid @RequestBody VerifyEmailDto dto) {
        return ResponseEntity.ok(authService.verifyEmail(dto));
    }

    @Operation(summary = "User login", description = "Authenticates a user and returns a JWT.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(summary = "Forgot password", description = "Initiates password reset flow and sends an OTP email.")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto dto) {
        authService.forgotPassword(dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Reset password", description = "Resets the user password using a valid OTP.")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordDto dto) {
        authService.resetPassword(dto);
        return ResponseEntity.ok().build();
    }
}
