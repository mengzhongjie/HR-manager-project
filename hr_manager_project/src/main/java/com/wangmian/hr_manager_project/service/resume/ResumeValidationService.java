package com.wangmian.hr_manager_project.service.resume;

import com.wangmian.hr_manager_project.util.PdfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeValidationService {

    private static final Logger log = LoggerFactory.getLogger(ResumeValidationService.class);

    /**
     * 校验上传的简历文件格式和大小是否符合要求
     *
     * @param file 上传的简历文件
     * @throws IllegalArgumentException 当文件大小超限、格式不正确或读取失败时抛出
     */
    public void validate(MultipartFile file) {
        PdfValidator.validate(file);
        log.info("Resume validation passed: {}", file.getOriginalFilename());
    }
}
