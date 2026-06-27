package com.wangmian.hr_manager_project.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "positions")
public class Position {
    @Id
    private String id;
    private String name;
    private String description;
    private String department;
    private String requirements;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
