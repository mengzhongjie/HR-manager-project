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
import java.nio.file.Files;

@Service
public class ResumeTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractionService.class);

    private final ScannedPdfOcrService ocrService;

    public ResumeTextExtractionService(ScannedPdfOcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * 从 MultipartFile 中提取 PDF 文本内容
     *
     * @param file 上传的 PDF 文件
     * @return 提取的文本内容
     * @throws IllegalArgumentException PDF 解析失败或内容为空时抛出
     */
    public String extractText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                return doExtract(document, bytes, file.getOriginalFilename());
            }
        } catch (IOException e) {
            log.error("PDF extraction failed for {}", file.getOriginalFilename(), e);
            throw new IllegalArgumentException("PDF文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 从 File 对象中提取 PDF 文本内容
     *
     * @param file PDF 文件
     * @return 提取的文本内容
     * @throws IllegalArgumentException PDF 解析失败或内容为空时抛出
     */
    public String extractText(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            try (PDDocument document = Loader.loadPDF(file)) {
                return doExtract(document, bytes, file.getName());
            }
        } catch (IOException e) {
            log.error("PDF extraction failed for {}", file.getName(), e);
            throw new IllegalArgumentException("PDF文件解析失败: " + e.getMessage());
        }
    }

    private String doExtract(PDDocument document, byte[] pdfBytes, String fileName) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        if (text == null || text.trim().isEmpty()) {
            log.info("PDFBox returned empty for {}, trying OCR fallback...", fileName);
            String ocrText = ocrService.ocr(pdfBytes, fileName);
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
