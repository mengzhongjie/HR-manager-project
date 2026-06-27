package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.service.CandidateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hr")
public class HrHomeController {

    private final CandidateService candidateService;

    public HrHomeController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("")
    public String home(Model model) {
        List<String> positions = candidateService.getAllPositions();
        Map<String, Long> positionCounts = positions.stream()
                .collect(Collectors.toMap(p -> p, p -> candidateService.countByPosition(p)));
        Map<String, Map<String, Long>> positionStatusCounts = positions.stream()
                .collect(Collectors.toMap(p -> p, p -> candidateService.getStatusCountByPosition(p)));
        model.addAttribute("positions", positions);
        model.addAttribute("positionCounts", positionCounts);
        model.addAttribute("positionStatusCounts", positionStatusCounts);
        return "hr/home";
    }
}
