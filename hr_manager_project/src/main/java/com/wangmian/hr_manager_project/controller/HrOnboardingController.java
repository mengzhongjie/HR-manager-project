package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Onboarding;
import com.wangmian.hr_manager_project.service.OnboardingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/hr/onboarding")
public class HrOnboardingController {

    private final OnboardingService onboardingService;

    public HrOnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/create")
    public String createForm(@RequestParam String offerId, Model model) {
        model.addAttribute("offerId", offerId);
        model.addAttribute("onboarding", new Onboarding());
        return "hr/onboarding/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam String offerId, @ModelAttribute Onboarding onboarding, Model model) {
        try {
            onboardingService.createOnboarding(offerId, onboarding);
            return "redirect:/hr/offers";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("offerId", offerId);
            return "hr/onboarding/form";
        }
    }
}
