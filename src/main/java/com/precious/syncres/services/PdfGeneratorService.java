package com.precious.syncres.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfGeneratorService {

    private final TemplateEngine templateEngine;
    private final FileStorageService fileStorageService;

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
            String fileId = UUID.randomUUID() + ".pdf";
            String storagePath = "retailored-cvs/" + fileId;
            
            fileStorageService.uploadBytes(storagePath, pdfBytes, "application/pdf");
            
            // 5. Generate fresh signed URL
            String downloadUrl = fileStorageService.generateSignedUrl(storagePath);
            
            PdfGenerationResult result = new PdfGenerationResult();
            result.setDownloadUrl(downloadUrl);
            result.setStoragePath(storagePath);
            result.setFileSizeBytes((long) pdfBytes.length);
            result.setExpiresAt(Instant.now().plusSeconds(pdfExpiryHours * 3600L));
            
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
