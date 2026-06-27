package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.Offer;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends MongoRepository<Offer, String> {
    Optional<Offer> findByCandidateId(String candidateId);
    List<Offer> findAllByOrderByCreatedAtDesc();
}
