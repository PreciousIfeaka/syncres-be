package com.precious.syncres.shared.util;

import java.security.SecureRandom;

public class OtpUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789";

    public static String generateOtp() {
        StringBuilder otp = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            otp.append(DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length())));
        }
        return otp.toString();
    }
}
