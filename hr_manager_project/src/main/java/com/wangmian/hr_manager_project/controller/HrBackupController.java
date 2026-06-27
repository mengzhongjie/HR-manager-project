package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.service.CandidateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/hr/positions/{position}/backup")
public class HrBackupController {

    private final CandidateService candidateService;

    public HrBackupController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("")
    public String backupList(@PathVariable String position, Model model) {
        List<Candidate> candidates = candidateService.findByPosition(position).stream()
                .filter(c -> c.getStatus() == CandidateStatus.PENDING_ARCHIVE)
                .toList();
        model.addAttribute("position", position);
        model.addAttribute("candidates", candidates);
        return "hr/candidates/backup";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable String position, @PathVariable String id) {
        candidateService.updateStatus(id, CandidateStatus.NEW, "HR", "从备选库恢复");
        return "redirect:/hr/positions/" + position + "/candidates/" + id;
    }
}
