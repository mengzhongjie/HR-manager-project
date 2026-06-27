package com.wangmian.hr_manager_project.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "offers")
public class Offer {
    @Id
    private String id;
    private String candidateId;
    private String candidateName;
    private String candidatePosition;
    private BigDecimal offeredSalary;
    private LocalDate offerDate;
    private LocalDate expiryDate;
    private Boolean accepted;
    private LocalDate responseDate;
    private String remark;
    private LocalDateTime createdAt;

    public Offer() {
        this.createdAt = LocalDateTime.now();
    }
}
