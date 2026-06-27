package com.wangmian.hr_manager_project.repository;

import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.enums.InterviewRound;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewRecordRepository extends MongoRepository<InterviewRecord, String> {
    List<InterviewRecord> findByCandidateIdOrderByRoundAsc(String candidateId);
    Optional<InterviewRecord> findByCandidateIdAndRound(String candidateId, InterviewRound round);
    @org.springframework.data.mongodb.repository.Query("{candidateId: ?0, round: ?1}")
    List<InterviewRecord> findCandidateIdAndRound(String candidateId, InterviewRound round);
    List<InterviewRecord> findAllByOrderByCreatedAtDesc();
}
