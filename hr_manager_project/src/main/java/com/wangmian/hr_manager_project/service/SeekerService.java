package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Seeker;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SeekerService {

    private static final Logger log = LoggerFactory.getLogger(SeekerService.class);

    private final SeekerRepository seekerRepository;
    private final CandidateRepository candidateRepository;

    public SeekerService(SeekerRepository seekerRepository, CandidateRepository candidateRepository) {
        this.seekerRepository = seekerRepository;
        this.candidateRepository = candidateRepository;
    }

    public Seeker registerOrGet(String username, String name, String email, String phone) {
        Optional<Seeker> existing = seekerRepository.findByUsername(username);
        if (existing.isPresent()) {
            return existing.get();
        }
        Seeker seeker = new Seeker();
        seeker.setUsername(username);
        seeker.setName(name);
        seeker.setEmail(email);
        seeker.setPhone(phone);
        seeker.setSubmissionCount(0);
        Seeker saved = seekerRepository.save(seeker);
        log.info("New seeker registered: {}", username);
        return saved;
    }

    /** 检查能否投递指定岗位：该岗位没有进行中的候选人 */
    public boolean canSubmit(String seekerId, String position) {
        List<Candidate> existing = candidateRepository.findBySeekerIdAndPosition(seekerId, position);
        for (Candidate c : existing) {
            CandidateStatus s = c.getStatus();
            // 进行中的状态不允许重复投递
            if (s != CandidateStatus.PENDING_ARCHIVE && s != CandidateStatus.REJECTED) {
                log.debug("Seeker {} cannot submit for {}: existing candidate status={}", seekerId, position, s);
                return false;
            }
        }
        return true;
    }

    /** 检查求职者ID是否有效 */
    public boolean exists(String seekerId) {
        return seekerRepository.existsById(seekerId);
    }

    /** 关联求职者和候选人（岗位维度） */
    public void linkCandidate(String seekerId, String candidateId, String position) {
        Seeker seeker = seekerRepository.findById(seekerId).orElseThrow();
        seeker.getPositionCandidates().put(position, candidateId);
        seeker.setSubmissionCount(seeker.getSubmissionCount() + 1);
        seekerRepository.save(seeker);
    }

    public Optional<Seeker> findById(String id) {
        return seekerRepository.findById(id);
    }

    public Optional<Seeker> findByUsername(String username) {
        return seekerRepository.findByUsername(username);
    }
}
