package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "hr.ocr.enabled", havingValue = "true", matchIfMissing = true)
public class ScannedPdfOcrService {

    private static final Logger log = LoggerFactory.getLogger(ScannedPdfOcrService.class);

    @Value("${hr.ocr.api-url:${hr.agent.resume-parse.ai.api-url:}}")
    private String apiUrl;

    @Value("${hr.ocr.api-key:${hr.agent.resume-parse.ai.api-key:}}")
    private String apiKey;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public ScannedPdfOcrService() {
        this.restClient = RestClient.create();
        this.mapper = new ObjectMapper();
    }

    /**
     * 上传 PDF 文件到 DeepSeek 文件 API 进行 OCR 识别
     * @param pdfBytes PDF 文件的字节数组
     * @param fileName 文件名（用于日志）
     * @return 提取的文字内容，失败返回 null
     */
    public String ocr(byte[] pdfBytes, String fileName) {
        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isEmpty()) {
            log.warn("OCR API not configured (url={}, key={}), skipping OCR", apiUrl, apiKey != null && !apiKey.isEmpty());
            return null;
        }

        try {
            // 构造 multipart/form-data 请求，上传 PDF 文件
            ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return fileName != null ? fileName : "resume.pdf";
                }
            };

            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", fileResource);

            String response = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                log.warn("OCR returned empty response for {}", fileName);
                return null;
            }

            // 尝试解析 JSON 响应，提取文本内容
            try {
                JsonNode root = mapper.readTree(response);
                // DeepSeek 文件 API 可能返回的内容字段格式
                String text = null;
                if (root.has("content")) {
                    text = root.get("content").asText();
                } else if (root.has("text")) {
                    text = root.get("text").asText();
                } else if (root.has("result")) {
                    text = root.get("result").asText();
                }
                if (text != null && !text.isBlank()) {
                    log.info("OCR extracted {} chars from {} via DeepSeek file API", text.length(), fileName);
                    return text;
                }
            } catch (Exception ignored) {
                // 不是 JSON 或结构不符，返回原始响应
            }

            // 后备：直接返回原始响应作为文本
            log.info("OCR returned {} chars (raw) from {}", response.length(), fileName);
            return response;

        } catch (Exception e) {
            log.error("OCR failed for {}: {}", fileName, e.getMessage());
            return null;
        }
    }
}
