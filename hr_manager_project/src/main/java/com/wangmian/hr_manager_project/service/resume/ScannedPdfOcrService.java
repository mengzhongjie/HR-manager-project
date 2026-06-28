package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
@ConditionalOnProperty(name = "hr.ocr.enabled", havingValue = "true", matchIfMissing = true)
public class ScannedPdfOcrService {

    private static final Logger log = LoggerFactory.getLogger(ScannedPdfOcrService.class);

    @Value("${hr.ocr.api-url:${hr.agent.resume-parse.ai.api-url:https://api.openai.com/v1/chat/completions}}")
    private String apiUrl;

    @Value("${hr.ocr.api-key:${hr.agent.resume-parse.ai.api-key:}}")
    private String apiKey;

    @Value("${hr.ocr.model:gpt-4o-mini}")
    private String model;

    @Value("${hr.ocr.page-limit:3}")
    private int pageLimit;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public ScannedPdfOcrService() {
        this.restClient = RestClient.create();
        this.mapper = new ObjectMapper();
    }

    /**
     * 对扫描件PDF进行OCR识别，返回提取的文字
     */
    public String ocr(PDDocument document) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OCR API key not configured, skipping OCR");
            return null;
        }

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = Math.min(document.getNumberOfPages(), pageLimit);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < totalPages; i++) {
                log.info("OCR processing page {}/{}", i + 1, totalPages);
                String pageText = ocrPage(renderer, i);
                if (pageText != null) {
                    result.append("--- 第 ").append(i + 1).append(" 页 ---\n");
                    result.append(pageText).append("\n\n");
                }
            }

            String text = result.toString().trim();
            if (text.isEmpty()) {
                log.warn("OCR returned empty text for all pages");
                return null;
            }
            log.info("OCR extracted {} chars from {} pages", text.length(), totalPages);
            return text;

        } catch (Exception e) {
            log.error("OCR failed", e);
            return null;
        }
    }

    private String ocrPage(PDFRenderer renderer, int pageIndex) throws Exception {
        // 渲染页面为图片（300 DPI 保证清晰度）
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);

        // 压缩为 JPEG base64
        String base64Image;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", baos);
            base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
        }

        // 构建 Vision API 请求
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                "请提取这张简历图片中的所有文字内容，保留原始排版和段落信息。返回纯文本，不要添加任何额外说明。"));

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", Map.of("url", "data:image/jpeg;base64," + base64Image));
        content.add(imagePart);

        userMessage.put("content", content);
        requestBody.put("messages", List.of(userMessage));
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", 4096);

        // 调用 API
        String response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = mapper.readTree(response);
        return root.path("choices").get(0).path("message").path("content").asText(null);
    }
}
