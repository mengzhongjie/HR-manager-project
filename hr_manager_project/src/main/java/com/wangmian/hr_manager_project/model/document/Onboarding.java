package com.wangmian.hr_manager_project.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "onboardings")
public class Onboarding {
    @Id
    private String id;
    private String candidateId;
    private String offerId;
    private String candidateName;
    private String candidatePosition;
    private LocalDate onboardDate;
    private String department;
    private String mentorName;
    private Boolean completed;
    private LocalDateTime createdAt;

    public Onboarding() {
        this.createdAt = LocalDateTime.now();
    }
}
