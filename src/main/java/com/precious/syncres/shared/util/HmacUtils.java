package com.precious.syncres.shared.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class HmacUtils {
    private static final String ALGORITHM = "HmacSHA256";

    public static String calculateHmac(String data, String secret) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    public static boolean verifyHmac(String data, String hmac, String secret) {
        String calculatedHmac = calculateHmac(data, secret);
        return calculatedHmac.equalsIgnoreCase(hmac);
    }
}
