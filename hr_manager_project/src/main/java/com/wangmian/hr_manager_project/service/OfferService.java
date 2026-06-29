package com.wangmian.hr_manager_project.service;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.OfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offerRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateService candidateService;

    public OfferService(OfferRepository offerRepository, CandidateRepository candidateRepository, CandidateService candidateService) {
        this.offerRepository = offerRepository;
        this.candidateRepository = candidateRepository;
        this.candidateService = candidateService;
    }

    /**
     * 为指定候选人创建 Offer，校验候选人状态和日期有效性，并更新候选人状态为 Offer 已发。
     *
     * @param candidateId 候选人 ID
     * @param offer       Offer 对象
     * @return 保存后的 Offer
     * @throws IllegalArgumentException 候选人不存在或日期无效时抛出
     * @throws IllegalStateException    候选人状态不是 WAITING_OFFER 时抛出
     */
    public Offer createOffer(String candidateId, Offer offer) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("候选人不存在"));

        if (candidate.getStatus() != CandidateStatus.WAITING_OFFER) {
            throw new IllegalStateException("候选人状态不是WAITING_OFFER，无法发放Offer");
        }

        if (offer.getOfferDate() != null && offer.getExpiryDate() != null
                && !offer.getExpiryDate().isAfter(offer.getOfferDate())) {
            throw new IllegalArgumentException("截止日期必须晚于Offer发送日期");
        }

        offer.setCandidateId(candidateId);
        offer.setCandidateName(candidate.getName());
        offer.setCandidatePosition(candidate.getPosition());

        Offer saved = offerRepository.save(offer);

        candidateService.updateStatus(candidateId, CandidateStatus.OFFERED, "HR", "发放Offer");
        log.info("Offer created for candidate {}: salary={}", candidateId, offer.getOfferedSalary());
        return saved;
    }

    /**
     * 根据候选人 ID 查询 Offer。
     *
     * @param candidateId 候选人 ID
     * @return Offer Optional
     */
    public Optional<Offer> findByCandidateId(String candidateId) {
        return offerRepository.findByCandidateId(candidateId);
    }

    /**
     * 根据 Offer ID 查询 Offer。
     *
     * @param id Offer ID
     * @return Offer Optional
     */
    public Optional<Offer> findById(String id) {
        return offerRepository.findById(id);
    }

    /**
     * 获取所有 Offer 记录，按创建时间降序排列。
     *
     * @return Offer 列表
     */
    public List<Offer> findAll() {
        return offerRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 求职者响应 Offer（接受或拒绝），更新 Offer 状态并同步更新候选人状态。
     *
     * @param offerId  Offer ID
     * @param accepted 是否接受
     * @param actor    操作人
     * @return 更新后的 Offer
     * @throws IllegalArgumentException Offer 不存在时抛出
     */
    public Offer respondToOffer(String offerId, boolean accepted, String actor) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Offer不存在"));
        offer.setAccepted(accepted);
        offer.setResponseDate(java.time.LocalDate.now());
        offerRepository.save(offer);

        String reason = accepted ? "求职者接受Offer" : "求职者拒绝Offer";
        CandidateStatus newStatus = accepted ? CandidateStatus.OFFERED : CandidateStatus.REJECTED;
        candidateService.updateStatus(offer.getCandidateId(), newStatus, actor, reason);
        log.info("Offer {} responded: accepted={}", offerId, accepted);
        return offer;
    }
}
