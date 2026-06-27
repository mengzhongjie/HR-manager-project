package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.Seeker;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SeekerRepository extends MongoRepository<Seeker, String> {
    Optional<Seeker> findByUsername(String username);
    boolean existsByUsername(String username);
}
