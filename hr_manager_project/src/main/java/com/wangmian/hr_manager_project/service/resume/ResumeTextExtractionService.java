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
            throw new IllegalArgumentException("PDF文件内容为空，无法解析");
        }
        log.info("Text extracted from {}: {} chars", fileName, text.length());
        return text;
    }
}
