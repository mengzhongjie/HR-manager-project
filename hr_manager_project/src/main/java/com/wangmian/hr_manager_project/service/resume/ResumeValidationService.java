package com.wangmian.hr_manager_project.service.resume;

import com.wangmian.hr_manager_project.util.PdfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeValidationService {

    private static final Logger log = LoggerFactory.getLogger(ResumeValidationService.class);

    public void validate(MultipartFile file) {
        PdfValidator.validate(file);
        log.info("Resume validation passed: {}", file.getOriginalFilename());
    }
}
