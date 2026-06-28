package com.wangmian.hr_manager_project.service.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class ResumeTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractionService.class);

    private final ScannedPdfOcrService ocrService;

    public ResumeTextExtractionService(ScannedPdfOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public String extractText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return doExtract(document, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("PDF extraction failed for {}", file.getOriginalFilename(), e);
            throw new IllegalArgumentException("PDF文件解析失败: " + e.getMessage());
        }
    }

    public String extractText(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            return doExtract(document, file.getName());
        } catch (IOException e) {
            log.error("PDF extraction failed for {}", file.getName(), e);
            throw new IllegalArgumentException("PDF文件解析失败: " + e.getMessage());
        }
    }

    private String doExtract(PDDocument document, String fileName) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        if (text == null || text.trim().isEmpty()) {
            log.info("PDFBox returned empty for {}, trying OCR fallback...", fileName);
            String ocrText = ocrService.ocr(document);
            if (ocrText != null && !ocrText.trim().isEmpty()) {
                log.info("OCR extracted {} chars from {}", ocrText.length(), fileName);
                return ocrText;
            }
            throw new IllegalArgumentException("PDF文件内容为空，无法解析。如果该PDF为扫描件，请确保已配置OCR API密钥");
        }
        log.info("Text extracted from {}: {} chars", fileName, text.length());
        return text;
    }
}
