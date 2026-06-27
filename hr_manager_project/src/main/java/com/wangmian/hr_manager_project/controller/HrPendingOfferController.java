package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/hr/offers/pending")
public class HrPendingOfferController {

    private final CandidateRepository candidateRepository;

    public HrPendingOfferController(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    @GetMapping("")
    public String pendingOffers(Model model) {
        List<Candidate> candidates = candidateRepository.findByStatus(CandidateStatus.WAITING_OFFER);
        model.addAttribute("candidates", candidates);
        return "hr/offers/pending";
    }
}
