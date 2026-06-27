package com.wangmian.hr_manager_project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

public class PdfValidator {

    private static final Logger log = LoggerFactory.getLogger(PdfValidator.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final byte[] PDF_MAGIC_BYTES = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};

    public static void validate(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制(最大10MB)，当前: " + file.getSize() / 1024 / 1024 + "MB");
        }
        if (file.getSize() == 0) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf") && !contentType.equals("application/octet-stream"))) {
            throw new IllegalArgumentException("仅支持PDF格式文件，当前类型: " + contentType);
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int bytesRead = is.read(header, 0, 4);
            if (bytesRead < 4) {
                throw new IllegalArgumentException("文件内容不完整，无法识别PDF格式");
            }
            for (int i = 0; i < 4; i++) {
                if (header[i] != PDF_MAGIC_BYTES[i]) {
                    throw new IllegalArgumentException("文件格式校验失败：不是有效的PDF文件");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("PDF校验IO异常", e);
            throw new IllegalArgumentException("文件读取失败，请重试");
        }
        log.info("PDF校验通过: {}", file.getOriginalFilename());
    }
}
