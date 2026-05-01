package com.precious.syncres.services;

import com.precious.syncres.entities.OtpToken;
import com.precious.syncres.entities.User;
import com.precious.syncres.repositories.OtpTokenRepository;
import com.precious.syncres.shared.util.OtpUtils;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Transactional
    public void sendEmailVerificationOtp(User user) {
        otpTokenRepository.deleteAllByUserIdAndPurpose(user.getId(), OtpToken.OtpPurpose.EMAIL_VERIFICATION);
        
        String otp = OtpUtils.generateOtp();
        saveOtpToken(user, otp, OtpToken.OtpPurpose.EMAIL_VERIFICATION);
        
        sendOtpEmail(user, otp, "email-otp", "Verify your Syncres account");
    }

    @Transactional
    public void sendPasswordResetOtp(User user) {
        otpTokenRepository.deleteAllByUserIdAndPurpose(user.getId(), OtpToken.OtpPurpose.PASSWORD_RESET);
        
        String otp = OtpUtils.generateOtp();
        saveOtpToken(user, otp, OtpToken.OtpPurpose.PASSWORD_RESET);
        
        sendOtpEmail(user, otp, "email-reset", "Reset your Syncres password");
    }

    private void saveOtpToken(User user, String otp, OtpToken.OtpPurpose purpose) {
        OtpToken token = OtpToken.builder()
                .user(user)
                .tokenHash(passwordEncoder.encode(otp))
                .purpose(purpose)
                .expiresAt(OffsetDateTime.now().plusMinutes(otpExpiryMinutes))
                .used(false)
                .build();
        otpTokenRepository.save(token);
    }

    private void sendOtpEmail(User user, String otp, String templateName, String subject) {
        try {
            Context context = new Context();
            context.setVariable("name", user.getFullName() != null ? user.getFullName() : "there");
            context.setVariable("otp", otp);
            context.setVariable("expiry", otpExpiryMinutes);
            
            String htmlContent = templateEngine.process(templateName, context);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(mailFrom);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("OTP email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", user.getEmail(), e);
            throw new RuntimeException("Email sending failed");
        }
    }
}
