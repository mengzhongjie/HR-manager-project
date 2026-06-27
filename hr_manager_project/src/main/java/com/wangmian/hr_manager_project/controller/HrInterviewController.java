package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.enums.InterviewRound;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.InterviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/hr/interviews")
public class HrInterviewController {

    private final InterviewService interviewService;
    private final CandidateService candidateService;

    public HrInterviewController(InterviewService interviewService, CandidateService candidateService) {
        this.interviewService = interviewService;
        this.candidateService = candidateService;
    }

    @GetMapping("")
    public String overview(Model model) {
        List<InterviewRecord> interviews = interviewService.getAllInterviews();
        model.addAttribute("interviews", interviews);
        return "hr/interviews/overview";
    }

    @GetMapping("/create")
    public String createForm(@RequestParam(required = false) String candidateId, Model model) {
        List<Candidate> candidates = candidateService.findByPosition(null);
        if (candidateId != null) {
            Candidate candidate = candidateService.findById(candidateId).orElse(null);
            model.addAttribute("selectedCandidate", candidate);
            List<InterviewRecord> existing = interviewService.getByCandidateId(candidateId);
            // Determine next round
            InterviewRound nextRound = InterviewRound.ROUND_1;
            if (existing.stream().anyMatch(r -> r.getRound() == InterviewRound.ROUND_1 && r.getResult() == com.wangmian.hr_manager_project.model.enums.InterviewResult.PASSED)) {
                nextRound = InterviewRound.ROUND_2;
            }
            if (existing.stream().anyMatch(r -> r.getRound() == InterviewRound.ROUND_2 && r.getResult() == com.wangmian.hr_manager_project.model.enums.InterviewResult.PASSED)) {
                nextRound = InterviewRound.ROUND_3;
            }
            model.addAttribute("nextRound", nextRound);
        }
        model.addAttribute("candidates", candidates);
        return "hr/interviews/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute InterviewRecord interview, Model model) {
        try {
            interviewService.saveInterview(interview);
            return "redirect:/hr/interviews";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return createForm(interview.getCandidateId(), model);
        }
    }
}
