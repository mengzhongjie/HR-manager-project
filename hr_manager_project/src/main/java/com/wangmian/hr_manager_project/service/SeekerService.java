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

    /**
     * 注册新求职者或获取已有求职者信息。
     *
     * @param username 用户名
     * @param name     姓名
     * @param email    邮箱
     * @param phone    电话
     * @return 求职者对象
     */
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

    /**
     * 检查求职者能否投递指定岗位：该岗位没有进行中的候选人。
     *
     * @param seekerId 求职者 ID
     * @param position 岗位名称
     * @return 可以投递返回 true，否则 false
     */
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

    /**
     * 检查求职者 ID 是否存在。
     *
     * @param seekerId 求职者 ID
     * @return 存在返回 true，否则 false
     */
    public boolean exists(String seekerId) {
        return seekerRepository.existsById(seekerId);
    }

    /**
     * 关联求职者和候选人（岗位维度）。
     *
     * @param seekerId    求职者 ID
     * @param candidateId 候选人 ID
     * @param position    岗位名称
     */
    public void linkCandidate(String seekerId, String candidateId, String position) {
        Seeker seeker = seekerRepository.findById(seekerId).orElseThrow();
        seeker.getPositionCandidates().put(position, candidateId);
        seeker.setSubmissionCount(seeker.getSubmissionCount() + 1);
        seekerRepository.save(seeker);
    }

    /**
     * 根据 ID 查询求职者。
     *
     * @param id 求职者 ID
     * @return 求职者 Optional
     */
    public Optional<Seeker> findById(String id) {
        return seekerRepository.findById(id);
    }

    /**
     * 根据用户名查询求职者。
     *
     * @param username 用户名
     * @return 求职者 Optional
     */
    public Optional<Seeker> findByUsername(String username) {
        return seekerRepository.findByUsername(username);
    }
}
