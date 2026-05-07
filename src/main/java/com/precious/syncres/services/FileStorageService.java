package com.precious.syncres.services;

import com.precious.syncres.shared.util.HmacUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Client s3Client;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @Value("${app.storage.pdf-expiry-hours}")
    private int expiryHours;

    public void uploadFile(String key, InputStream inputStream, long size, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromInputStream(inputStream, size));
    }

    public void uploadBytes(String key, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromBytes(content));
    }

    public ResponseInputStream<GetObjectResponse> downloadFile(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    /**
     * Generates a signed download URL for a file.
     * key: The full S3 key (e.g. user-cvs/uuid or retailored-cvs/uuid)
     */
    public String generateSignedUrl(String key) {
        long expires = Instant.now().plusSeconds(expiryHours * 3600L).getEpochSecond();
        // Use the key itself in the signature to prevent tampering
        String dataToSign = key + ":" + expires;
        String signature = HmacUtils.calculateHmac(dataToSign, hmacSecret);
        
        return String.format("/api/match/download?key=%s&expires=%d&sig=%s", key, expires, signature);
    }

    public void validateSignature(String key, long expires, String sig) {
        if (Instant.now().getEpochSecond() > expires) {
            throw new RuntimeException("Link expired");
        }
        String dataToVerify = key + ":" + expires;
        if (!HmacUtils.verifyHmac(dataToVerify, sig, hmacSecret)) {
            throw new RuntimeException("Invalid signature");
        }
    }
}
