package com.wangmian.hr_manager_project.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "seekers")
public class Seeker {
    @Id
    private String id;
    private String username;
    private String name;
    private String email;
    private String phone;
    private String activeCandidateId;
    private int submissionCount;
    private LocalDateTime createdAt;

    public Seeker() {
        this.createdAt = LocalDateTime.now();
    }
}
