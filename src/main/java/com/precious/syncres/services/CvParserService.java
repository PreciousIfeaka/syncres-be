package com.precious.syncres.services;

import com.precious.syncres.repositories.CvDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class CvParserService {
    private final CvDocumentRepository cvDocumentRepository;
    private final S3Client s3Client;

    public CvParserService(
            CvDocumentRepository cvDocumentRepository,
            S3Client s3Client
            ) {
        this.cvDocumentRepository = cvDocumentRepository;
        this.s3Client = s3Client;
    }

    @Value("${app.matcher.max-cv-chars:15000}")
    private int maxCvChars;

    @Value("${s3.bucket-name}")
    private String bucketName;

    public String parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        if (filename == null) throw new IllegalArgumentException("Filename is missing");

        try (InputStream is = file.getInputStream()) {
            String text;
            String lowerFilename = filename.toLowerCase();
            
            if (lowerFilename.endsWith(".pdf") || "application/pdf".equals(contentType)) {
                text = parsePdf(is);
            } else if (lowerFilename.endsWith(".docx") || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
                text = parseDocx(is);
            } else if (lowerFilename.endsWith(".doc") || "application/msword".equals(contentType)) {
                text = parseDoc(is);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + filename);
            }

            return postProcess(text);
        } catch (IOException e) {
            log.error("Failed to parse CV file", e);
            throw new RuntimeException("Failed to parse CV file: " + e.getMessage());
        }
    }

    private String parsePdf(InputStream is) throws IOException {
        try (PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // v2 requirement
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream is) throws IOException {
        try (XWPFDocument document = new XWPFDocument(is)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        }
    }

    private String parseDoc(InputStream is) throws IOException {
        try (HWPFDocument document = new HWPFDocument(is)) {
            WordExtractor extractor = new WordExtractor(document);
            return extractor.getText();
        }
    }

    private String postProcess(String text) {
        if (text == null) return "";
        
        String processed = text.replace("\u0000", "")
                .replaceAll("\\r\\n|\\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        if (processed.length() > maxCvChars) {
            log.warn("CV text truncated from {} to {} characters", processed.length(), maxCvChars);
            processed = processed.substring(0, maxCvChars);
        }

        return processed;
    }
}
