package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.Onboarding;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OnboardingRepository extends MongoRepository<Onboarding, String> {
    Optional<Onboarding> findByCandidateId(String candidateId);
}
