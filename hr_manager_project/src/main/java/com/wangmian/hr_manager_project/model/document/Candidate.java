package com.wangmian.hr_manager_project.model.document;

import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.model.enums.EducationLevel;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "candidates")
public class Candidate {
    @Id
    private String id;
    private String seekerId;
    private String name;
    private Integer age;
    private String email;
    private String phone;
    private String position;
    private Integer yearsOfExperience;
    private Boolean isFreshGraduate;
    private Integer graduationYear;
    private EducationLevel educationLevel;
    private String school;
    private String major;
    private List<String> techStack;
    private String workHistory;
    private String selfEvaluation;
    private String resumeFileName;
    private String resumeFilePath;
    private CandidateStatus status;
    private int interviewRound;
    private Integer qualificationScore;
    private AiQualification aiQualification;
    private List<StatusHistoryEntry> statusHistory;
    @Version
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Candidate() {
        this.status = CandidateStatus.NEW;
        this.interviewRound = 0;
        this.statusHistory = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Data
    public static class AiQualification {
        private int score;
        private String recommendation;
    }

    @Data
    public static class StatusHistoryEntry {
        private String eventId;
        private CandidateStatus fromStatus;
        private CandidateStatus toStatus;
        private LocalDateTime timestamp;
        private String actor;
        private String reason;
    }
}
