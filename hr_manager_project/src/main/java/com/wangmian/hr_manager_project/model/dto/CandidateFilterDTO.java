package com.wangmian.hr_manager_project.model.dto;

import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.model.enums.EducationLevel;
import lombok.Data;

@Data
public class CandidateFilterDTO {
    private String nameKeyword;
    private CandidateStatus status;
    private Boolean isFreshGraduate;
    private Integer minGraduationYear;
    private Integer maxGraduationYear;
    private Integer minAge;
    private Integer maxAge;
    private String techStack;
    private Integer minExperience;
    private Integer maxExperience;
    private EducationLevel minEducationLevel;
    private Integer minAiScore;
    private Integer maxAiScore;
}
