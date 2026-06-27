package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Seeker;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.SeekerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public boolean canSubmit(String seekerId) {
        Seeker seeker = seekerRepository.findById(seekerId).orElse(null);
        if (seeker == null) return true;
        if (seeker.getActiveCandidateId() == null) return true;
        Optional<Candidate> active = candidateRepository.findById(seeker.getActiveCandidateId());
        if (active.isEmpty()) return true;
        CandidateStatus status = active.get().getStatus();
        boolean canSubmit = status == CandidateStatus.PENDING_ARCHIVE || status == CandidateStatus.REJECTED;
        log.debug("Seeker {} canSubmit: {} (status={})", seekerId, canSubmit, status);
        return canSubmit;
    }

    public void linkCandidate(String seekerId, String candidateId) {
        Seeker seeker = seekerRepository.findById(seekerId).orElseThrow();
        seeker.setActiveCandidateId(candidateId);
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
