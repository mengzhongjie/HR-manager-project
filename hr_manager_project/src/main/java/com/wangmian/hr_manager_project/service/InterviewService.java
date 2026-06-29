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

    /**
     * 根据候选人 ID 查询其所有面试记录，按轮次升序排列。
     *
     * @param candidateId 候选人 ID
     * @return 面试记录列表
     */
    public List<InterviewRecord> getByCandidateId(String candidateId) {
        return interviewRepository.findByCandidateIdOrderByRoundAsc(candidateId);
    }

    /**
     * 获取所有面试记录，按创建时间降序排列。
     *
     * @return 所有面试记录列表
     */
    public List<InterviewRecord> getAllInterviews() {
        return interviewRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 保存面试记录，校验面试轮次顺序并根据面试结果自动推进候选人状态。
     *
     * @param interview 面试记录
     * @return 保存后的面试记录
     * @throws IllegalArgumentException 候选人不存在时抛出
     * @throws IllegalStateException    上一轮未通过时抛出
     */
    public InterviewRecord saveInterview(InterviewRecord interview) {
        Candidate candidate = candidateRepository.findById(interview.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("候选人不存在"));

        // Validate round order (检查是否有任意一条上一轮记录是通过的，防止重复数据导致非唯一结果错误)
        if (interview.getRound() == InterviewRound.ROUND_2) {
            List<InterviewRecord> prev = interviewRepository.findCandidateIdAndRound(
                    interview.getCandidateId(), InterviewRound.ROUND_1);
            boolean passed = prev.stream().anyMatch(r -> r.getResult() == InterviewResult.PASSED);
            if (prev.isEmpty() || !passed) {
                throw new IllegalStateException("一面未通过，无法进入二面");
            }
        }
        if (interview.getRound() == InterviewRound.ROUND_3) {
            List<InterviewRecord> prev = interviewRepository.findCandidateIdAndRound(
                    interview.getCandidateId(), InterviewRound.ROUND_2);
            boolean passed = prev.stream().anyMatch(r -> r.getResult() == InterviewResult.PASSED);
            if (prev.isEmpty() || !passed) {
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

        // 邀约/接受流程由 invite-interview + 求职者响应处理
        // 此处只根据面试结果推进状态

        if (interview.getResult() == InterviewResult.FAILED) {
            String reason = "第" + roundNum + "面未通过";
            candidate = candidateService.updateStatus(candidate.getId(), CandidateStatus.REJECTED, "HR", reason);
        } else if (interview.getResult() == InterviewResult.PASSED) {
            CandidateStatus newStatus = switch (interview.getRound()) {
                case ROUND_1 -> CandidateStatus.ROUND_1_PASSED;
                case ROUND_2 -> CandidateStatus.ROUND_2_PASSED;
                case ROUND_3 -> CandidateStatus.WAITING_OFFER;
            };
            String reason = switch (interview.getRound()) {
                case ROUND_1 -> "一面通过";
                case ROUND_2 -> "二面通过";
                case ROUND_3 -> "三面通过，等待发放Offer";
            };
            candidate = candidateService.updateStatus(candidate.getId(), newStatus, "HR", reason);
        }

        log.info("Interview saved: {} round={} result={}", interview.getCandidateName(), interview.getRound(), interview.getResult());
        return saved;
    }
}
