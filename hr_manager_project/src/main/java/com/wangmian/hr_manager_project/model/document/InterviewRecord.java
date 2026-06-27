package com.wangmian.hr_manager_project.model.document;

import com.wangmian.hr_manager_project.model.enums.InterviewResult;
import com.wangmian.hr_manager_project.model.enums.InterviewRound;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "interviews")
public class InterviewRecord {
    @Id
    private String id;
    private String candidateId;
    private String candidateName;
    private String candidatePosition;
    private InterviewRound round;
    private String interviewerName;
    private LocalDate interviewDate;
    private InterviewResult result;
    private Integer score;
    private String feedback;
    private LocalDateTime createdAt;

    public InterviewRecord() {
        this.createdAt = LocalDateTime.now();
    }
}
