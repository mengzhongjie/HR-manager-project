package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CandidateRepository extends MongoRepository<Candidate, String> {
    List<Candidate> findByPosition(String position);
    List<Candidate> findByPositionAndStatus(String position, CandidateStatus status);
    List<Candidate> findByStatus(CandidateStatus status);
    List<Candidate> findByPositionAndStatusIn(String position, List<CandidateStatus> statuses);
    List<Candidate> findByStatusIn(List<CandidateStatus> statuses);
    long countByPosition(String position);
    List<String> findDistinctPositionBy();
}
