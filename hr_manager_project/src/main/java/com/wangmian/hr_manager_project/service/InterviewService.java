package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.model.enums.InterviewResult;
import com.wangmian.hr_manager_project.model.enums.InterviewRound;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    private final InterviewRecordRepository interviewRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;

    public InterviewService(InterviewRecordRepository interviewRepository,
                            CandidateRepository candidateRepository,
                            CandidateService candidateService) {
        this.interviewRepository = interviewRepository;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
    }

    public List<InterviewRecord> getByCandidateId(String candidateId) {
        return interviewRepository.findByCandidateIdOrderByRoundAsc(candidateId);
    }

    public List<InterviewRecord> getAllInterviews() {
        return interviewRepository.findAllByOrderByCreatedAtDesc();
    }

    public InterviewRecord saveInterview(InterviewRecord interview) {
        Candidate candidate = candidateRepository.findById(interview.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("候选人不存在"));

        // Validate round order
        if (interview.getRound() == InterviewRound.ROUND_2) {
            Optional<InterviewRecord> round1 = interviewRepository.findByCandidateIdAndRound(
                    interview.getCandidateId(), InterviewRound.ROUND_1);
            if (round1.isEmpty() || round1.get().getResult() != InterviewResult.PASSED) {
                throw new IllegalStateException("一面未通过，无法进入二面");
            }
        }
        if (interview.getRound() == InterviewRound.ROUND_3) {
            Optional<InterviewRecord> round2 = interviewRepository.findByCandidateIdAndRound(
                    interview.getCandidateId(), InterviewRound.ROUND_2);
            if (round2.isEmpty() || round2.get().getResult() != InterviewResult.PASSED) {
                throw new IllegalStateException("二面未通过，无法进入三面");
            }
        }

        interview.setCandidateName(candidate.getName());
        interview.setCandidatePosition(candidate.getPosition());

        InterviewRecord saved = interviewRepository.save(interview);

        // Update candidate interview round
        int roundNum = switch (interview.getRound()) {
            case ROUND_1 -> 1;
            case ROUND_2 -> 2;
            case ROUND_3 -> 3;
        };
        candidate.setInterviewRound(roundNum);

        // Auto-advance status based on result
        if (candidate.getStatus() == CandidateStatus.INTERVIEW_INVITED || candidate.getStatus() == CandidateStatus.NEW) {
            candidate.setStatus(CandidateStatus.IN_INTERVIEW);
        }

        if (interview.getResult() == InterviewResult.FAILED) {
            String reason = "第" + roundNum + "面未通过";
            candidate = candidateService.updateStatus(candidate.getId(), CandidateStatus.REJECTED, "HR", reason);
        } else if (interview.getResult() == InterviewResult.PASSED && interview.getRound() == InterviewRound.ROUND_3) {
            // Three rounds passed -> move to WAITING_OFFER
            String reason = "三面通过，等待发放Offer";
            candidate = candidateService.updateStatus(candidate.getId(), CandidateStatus.WAITING_OFFER, "HR", reason);
        } else {
            candidateRepository.save(candidate);
        }

        log.info("Interview saved: {} round={} result={}", interview.getCandidateName(), interview.getRound(), interview.getResult());
        return saved;
    }
}
