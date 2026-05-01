package com.precious.syncres.services;

import com.precious.syncres.shared.util.HmacUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfGeneratorService {

    private final TemplateEngine templateEngine;
    private final S3Client s3Client;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @Value("${app.storage.pdf-expiry-hours:24}")
    private int pdfExpiryHours;

    public PdfGenerationResult generate(CvRewriterService.RewriteResult rewriteResult) {
        try {
            // 1. Populate Thymeleaf context
            Context context = new Context();
            context.setVariable("cv", rewriteResult);
            
            // 2. Render HTML
            String html = templateEngine.process("cv-template", context);
            
            // 3. Convert HTML to PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(baos);
            byte[] pdfBytes = baos.toByteArray();
            
            // 4. Upload to S3
            String fileId = UUID.randomUUID().toString() + ".pdf";
            String storagePath = "retailored-cvs/" + fileId;
            
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storagePath)
                    .contentType("application/pdf")
                    .build(), RequestBody.fromBytes(pdfBytes));
            
            // 5. Generate HMAC-signed URL
            long expiry = Instant.now().plusSeconds(pdfExpiryHours * 3600L).getEpochSecond();
            String dataToSign = fileId + ":" + expiry;
            String signature = HmacUtils.calculateHmac(dataToSign, hmacSecret);
            
            String downloadUrl = String.format("/api/match/download/%s?expires=%d&sig=%s", fileId, expiry, signature);
            
            PdfGenerationResult result = new PdfGenerationResult();
            result.setDownloadUrl(downloadUrl);
            result.setStoragePath(storagePath);
            result.setFileSizeBytes((long) pdfBytes.length);
            result.setExpiresAt(Instant.ofEpochSecond(expiry));
            
            return result;
        } catch (Exception e) {
            log.error("Failed to generate PDF", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class PdfGenerationResult {
        private String downloadUrl;
        private String storagePath;
        private Long fileSizeBytes;
        private Instant expiresAt;
    }
}
