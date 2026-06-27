package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.dto.CandidateFilterDTO;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import com.wangmian.hr_manager_project.service.CandidateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/hr/positions/{position}")
public class HrCandidateController {

    private final CandidateService candidateService;
    private final InterviewRecordRepository interviewRepository;

    public HrCandidateController(CandidateService candidateService, InterviewRecordRepository interviewRepository) {
        this.candidateService = candidateService;
        this.interviewRepository = interviewRepository;
    }

    @GetMapping("/candidates")
    public String list(@PathVariable String position, CandidateFilterDTO filter, Model model) {
        List<Candidate> candidates = candidateService.filterByPosition(position, filter);
        model.addAttribute("position", position);
        model.addAttribute("candidates", candidates);
        model.addAttribute("filter", filter);
        return "hr/candidates/list";
    }

    @GetMapping("/candidates/{id}")
    public String detail(@PathVariable String position, @PathVariable String id, Model model) {
        Candidate candidate = candidateService.findById(id).orElseThrow();
        List<InterviewRecord> interviews = interviewRepository.findByCandidateIdOrderByRoundAsc(id);
        model.addAttribute("position", position);
        model.addAttribute("candidate", candidate);
        model.addAttribute("interviews", interviews);
        return "hr/candidates/detail";
    }

    @PostMapping("/candidates/{id}/status")
    public String updateStatus(@PathVariable String position, @PathVariable String id,
                               @RequestParam CandidateStatus status, @RequestParam(defaultValue = "HR") String actor,
                               @RequestParam(defaultValue = "") String reason, Model model) {
        try {
            candidateService.updateStatus(id, status, actor, reason.isEmpty() ? "HR手动操作" : reason);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/hr/positions/" + position + "/candidates/" + id;
    }

    @PostMapping("/candidates/{id}/qualify")
    public String aiQualify(@PathVariable String position, @PathVariable String id) {
        try {
            candidateService.runAiQualify(id);
        } catch (Exception e) {
            // AI qualify is best-effort
        }
        return "redirect:/hr/positions/" + position + "/candidates/" + id;
    }
}
