package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Onboarding;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.OfferRepository;
import com.wangmian.hr_manager_project.repository.OnboardingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingRepository onboardingRepository;
    private final OfferRepository offerRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;

    public OnboardingService(OnboardingRepository onboardingRepository, OfferRepository offerRepository,
                             CandidateRepository candidateRepository, CandidateService candidateService) {
        this.onboardingRepository = onboardingRepository;
        this.offerRepository = offerRepository;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
    }

    public Onboarding createOnboarding(String offerId, Onboarding onboarding) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer不存在"));

        if (!Boolean.TRUE.equals(offer.getAccepted())) {
            throw new IllegalStateException("Offer未被接受，无法登记入职");
        }

        Candidate candidate = candidateRepository.findById(offer.getCandidateId())
                .orElseThrow(() -> new IllegalArgumentException("候选人不存在"));

        onboarding.setCandidateId(candidate.getId());
        onboarding.setOfferId(offerId);
        onboarding.setCandidateName(candidate.getName());
        onboarding.setCandidatePosition(candidate.getPosition());
        onboarding.setCompleted(false);

        Onboarding saved = onboardingRepository.save(onboarding);

        candidateService.updateStatus(candidate.getId(), CandidateStatus.ONBOARDED, "HR", "入职登记完成");
        log.info("Onboarding created for candidate {}", candidate.getId());
        return saved;
    }

    public Optional<Onboarding> findByCandidateId(String candidateId) {
        return onboardingRepository.findByCandidateId(candidateId);
    }

    public Optional<Onboarding> findById(String id) {
        return onboardingRepository.findById(id);
    }
}
