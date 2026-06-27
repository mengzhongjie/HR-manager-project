package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.agent.AgentRegistry;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Candidate.AiQualification;
import com.wangmian.hr_manager_project.model.document.Candidate.StatusHistoryEntry;
import com.wangmian.hr_manager_project.model.dto.CandidateFilterDTO;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final AgentRegistry agentRegistry;
    private final StringRedisTemplate redis;

    public CandidateService(CandidateRepository candidateRepository, AgentRegistry agentRegistry, StringRedisTemplate redis) {
        this.candidateRepository = candidateRepository;
        this.agentRegistry = agentRegistry;
        this.redis = redis;
    }

    public Candidate save(Candidate candidate) {
        if (candidate.getStatus() == null) candidate.setStatus(CandidateStatus.NEW);
        candidate.setUpdatedAt(LocalDateTime.now());
        return candidateRepository.save(candidate);
    }

    public Optional<Candidate> findById(String id) {
        return candidateRepository.findById(id);
    }

    public List<Candidate> findByPosition(String position) {
        return candidateRepository.findByPosition(position);
    }

    public List<String> getAllPositions() {
        return candidateRepository.findDistinctPositions();
    }

    public List<Candidate> filterByPosition(String position, CandidateFilterDTO filter) {
        List<Candidate> candidates = candidateRepository.findByPosition(position);
        return applyFilters(candidates, filter);
    }

    public List<Candidate> filterByStatus(CandidateStatus status) {
        return candidateRepository.findByStatus(status);
    }

    private List<Candidate> applyFilters(List<Candidate> candidates, CandidateFilterDTO filter) {
        if (filter == null) return candidates;
        return candidates.stream()
                .filter(c -> filter.getNameKeyword() == null || filter.getNameKeyword().isEmpty()
                        || (c.getName() != null && c.getName().toLowerCase().contains(filter.getNameKeyword().toLowerCase())))
                .filter(c -> filter.getStatus() == null || c.getStatus() == filter.getStatus())
                .filter(c -> filter.getIsFreshGraduate() == null || Objects.equals(c.getIsFreshGraduate(), filter.getIsFreshGraduate()))
                .filter(c -> filter.getMinGraduationYear() == null || (c.getGraduationYear() != null && c.getGraduationYear() >= filter.getMinGraduationYear()))
                .filter(c -> filter.getMaxGraduationYear() == null || (c.getGraduationYear() != null && c.getGraduationYear() <= filter.getMaxGraduationYear()))
                .filter(c -> filter.getMinAge() == null || (c.getAge() != null && c.getAge() >= filter.getMinAge()))
                .filter(c -> filter.getMaxAge() == null || (c.getAge() != null && c.getAge() <= filter.getMaxAge()))
                .filter(c -> filter.getTechStack() == null || filter.getTechStack().isEmpty()
                        || (c.getTechStack() != null && c.getTechStack().stream().anyMatch(s -> s.toLowerCase().contains(filter.getTechStack().toLowerCase()))))
                .filter(c -> filter.getMinExperience() == null || (c.getYearsOfExperience() != null && c.getYearsOfExperience() >= filter.getMinExperience()))
                .filter(c -> filter.getMaxExperience() == null || (c.getYearsOfExperience() != null && c.getYearsOfExperience() <= filter.getMaxExperience()))
                .filter(c -> filter.getMinEducationLevel() == null || (c.getEducationLevel() != null && c.getEducationLevel().compareTo(filter.getMinEducationLevel()) >= 0))
                .filter(c -> filter.getMinAiScore() == null || (c.getAiQualification() != null && c.getAiQualification().getScore() >= filter.getMinAiScore()))
                .filter(c -> filter.getMaxAiScore() == null || (c.getAiQualification() != null && c.getAiQualification().getScore() <= filter.getMaxAiScore()))
                .collect(Collectors.toList());
    }

    public Candidate updateStatus(String candidateId, CandidateStatus newStatus, String actor, String reason) {
        // Redis distributed lock
        String lockKey = "state_lock:candidate:" + candidateId;
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "locked", Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) {
            throw new IllegalStateException("候选人正在被其他操作处理，请稍后重试");
        }
        try {
            Candidate candidate = candidateRepository.findById(candidateId)
                    .orElseThrow(() -> new IllegalArgumentException("候选人不存在: " + candidateId));
            CandidateStatus oldStatus = candidate.getStatus();
            validateStatusTransition(oldStatus, newStatus);

            // Record event sourcing entry
            StatusHistoryEntry entry = new StatusHistoryEntry();
            entry.setEventId(UUID.randomUUID().toString());
            entry.setFromStatus(oldStatus);
            entry.setToStatus(newStatus);
            entry.setTimestamp(LocalDateTime.now());
            entry.setActor(actor);
            entry.setReason(reason);
            candidate.getStatusHistory().add(entry);

            candidate.setStatus(newStatus);
            candidate.setUpdatedAt(LocalDateTime.now());
            Candidate saved = candidateRepository.save(candidate);
            log.info("Status updated: {} {} -> {} (by {})", candidateId, oldStatus, newStatus, actor);
            return saved;
        } finally {
            redis.delete(lockKey);
        }
    }

    private void validateStatusTransition(CandidateStatus from, CandidateStatus to) {
        if (from == to) return;
        switch (from) {
            case NEW -> {
                if (to != CandidateStatus.PENDING_ARCHIVE && to != CandidateStatus.INTERVIEW_INVITED && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("NEW只能转为存档/邀请面试/淘汰");
            }
            case INTERVIEW_INVITED -> {
                if (to != CandidateStatus.IN_INTERVIEW && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("邀约中只能转为面试中/淘汰");
            }
            case IN_INTERVIEW -> {
                if (to != CandidateStatus.ROUND_1_PASSED && to != CandidateStatus.ROUND_2_PASSED
                        && to != CandidateStatus.WAITING_OFFER && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("面试中只能转为对应轮次通过/待Offer/淘汰");
            }
            case ROUND_1_PASSED -> {
                if (to != CandidateStatus.INTERVIEW_INVITED && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("一面通过只能转为下一轮邀约/淘汰");
            }
            case ROUND_2_PASSED -> {
                if (to != CandidateStatus.INTERVIEW_INVITED && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("二面通过只能转为下一轮邀约/淘汰");
            }
            case WAITING_OFFER -> {
                if (to != CandidateStatus.OFFERED && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("待发Offer只能转为已发Offer/淘汰");
            }
            case OFFERED -> {
                if (to != CandidateStatus.ONBOARDED && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("已发Offer只能转为已入职/淘汰");
            }
            case PENDING_ARCHIVE -> {
                if (to != CandidateStatus.NEW && to != CandidateStatus.REJECTED)
                    throw new IllegalStateException("备选库只能转为新候选人/淘汰");
            }
            case ONBOARDED, REJECTED -> throw new IllegalStateException("终态不可变更");
        }
    }

    public void runAiQualify(String candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("候选人不存在: " + candidateId));
        AiQualification aiResult = agentRegistry.execute("candidate-qualify", candidate);
        candidate.setAiQualification(aiResult);
        candidate.setUpdatedAt(LocalDateTime.now());
        candidateRepository.save(candidate);
        log.info("AI qualify complete for {}: score={}", candidateId, aiResult.getScore());
    }

    public long countByPosition(String position) {
        return candidateRepository.countByPosition(position);
    }

    public Map<String, Long> getStatusCountByPosition(String position) {
        Map<String, Long> counts = new HashMap<>();
        for (CandidateStatus status : CandidateStatus.values()) {
            counts.put(status.name(), 0L);
        }
        candidateRepository.findByPosition(position).stream()
                .filter(c -> c.getStatus() != null)
                .collect(Collectors.groupingBy(c -> c.getStatus().name(), Collectors.counting()))
                .forEach(counts::put);
        return counts;
    }
}
