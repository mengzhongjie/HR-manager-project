package com.wangmian.hr_manager_project.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedResumeDTO {
    private String name;
    private String email;
    private String phone;
    private String position;
    private Integer yearsOfExperience;
    private Boolean isFreshGraduate;
    private Integer graduationYear;
    private String educationLevel;
    private String school;
    private String major;
    private List<String> techStack;
    private String workHistory;
    private String selfEvaluation;
}
