package com.precious.syncres.services;

import com.precious.syncres.entities.OtpToken;
import com.precious.syncres.entities.User;
import com.precious.syncres.repositories.OtpTokenRepository;
import com.precious.syncres.repositories.UserRepository;
import com.precious.syncres.shared.dto.*;
import com.precious.syncres.shared.exception.AppException;
import com.precious.syncres.shared.exception.ErrorCode;
import com.precious.syncres.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final JobScheduler jobScheduler;
    private final EmailJobService emailJobService;

    @Transactional
    public void register(RegisterRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }

        User user = User.builder()
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getFullName())
                .emailVerified(false)
                .build();
        userRepository.save(user);

        jobScheduler.enqueue(() -> emailJobService.sendVerificationOtp(user.getId()));
    }

    @Transactional
    public AuthResponseDto verifyEmail(VerifyEmailDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "User not found"));

        OtpToken token = otpTokenRepository.findLatestValidToken(user.getId(), OtpToken.OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, "No valid verification token found"));

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "Verification token has expired");
        }

        if (!passwordEncoder.matches(dto.getOtp(), token.getTokenHash())) {
            throw new AppException(ErrorCode.OTP_INVALID, "Invalid verification code");
        }

        token.setUsed(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        // Auto-login after verification or require manual login? 
        // Specs say "/verify-email returns a AuthResponseDto (JWT issued on success)"
        // But authenticationManager.authenticate requires the raw password which we don't have here.
        // We can manually generate the JWT since we've verified the email.
        
        String jwt = tokenProvider.generateTokenManual(user.getId().toString()); // Need to add this method

        return AuthResponseDto.builder()
                .token(jwt)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    public AuthResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.JOB_ACCESS_DENIED, "Invalid credentials"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.JOB_ACCESS_DENIED, "Invalid credentials");
        }

        if (!user.isEmailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED, "Email not verified. Please check your inbox.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        String jwt = tokenProvider.generateToken(authentication);

        return AuthResponseDto.builder()
                .token(jwt)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequestDto dto) {
        userRepository.findByEmail(dto.getEmail()).ifPresent(user -> {
            jobScheduler.enqueue(() -> emailJobService.sendPasswordResetOtp(user.getId()));
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, "User not found"));

        OtpToken token = otpTokenRepository.findLatestValidToken(user.getId(), OtpToken.OtpPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, "No valid reset token found"));

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "Reset token has expired");
        }

        if (!passwordEncoder.matches(dto.getOtp(), token.getTokenHash())) {
            throw new AppException(ErrorCode.OTP_INVALID, "Invalid reset code");
        }

        token.setUsed(true);
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}
