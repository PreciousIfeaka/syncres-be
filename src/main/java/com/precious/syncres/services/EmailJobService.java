package com.precious.syncres.services;

import com.precious.syncres.entities.User;
import com.precious.syncres.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailJobService {

    private final EmailService emailService;
    private final UserRepository userRepository;

    public void sendVerificationOtp(UUID userId) {
        userRepository.findById(userId).ifPresent(emailService::sendEmailVerificationOtp);
    }

    public void sendPasswordResetOtp(UUID userId) {
        userRepository.findById(userId).ifPresent(emailService::sendPasswordResetOtp);
    }
}
