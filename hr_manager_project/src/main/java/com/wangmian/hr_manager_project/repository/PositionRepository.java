package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.Position;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PositionRepository extends MongoRepository<Position, String> {
    boolean existsByName(String name);
}
