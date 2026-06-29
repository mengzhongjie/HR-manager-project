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

    /**
     * 为已接受 Offer 的候选人创建入职登记，并更新候选人状态为已入职。
     *
     * @param offerId   Offer ID
     * @param onboarding 入职登记对象
     * @return 保存后的入职记录
     * @throws IllegalArgumentException Offer 或候选人不存在时抛出
     * @throws IllegalStateException    Offer 未被接受时抛出
     */
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

    /**
     * 根据候选人 ID 查询入职登记。
     *
     * @param candidateId 候选人 ID
     * @return 入职记录 Optional
     */
    public Optional<Onboarding> findByCandidateId(String candidateId) {
        return onboardingRepository.findByCandidateId(candidateId);
    }

    /**
     * 根据 ID 查询入职登记。
     *
     * @param id 入职记录 ID
     * @return 入职记录 Optional
     */
    public Optional<Onboarding> findById(String id) {
        return onboardingRepository.findById(id);
    }
}
