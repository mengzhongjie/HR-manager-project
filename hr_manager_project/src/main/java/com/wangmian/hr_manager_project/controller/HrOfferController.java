package com.wangmian.hr_manager_project.controller;

import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.service.OfferService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/hr/offers")
public class HrOfferController {

    private final OfferService offerService;

    public HrOfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping("")
    public String list(Model model) {
        List<Offer> offers = offerService.findAll();
        model.addAttribute("offers", offers);
        return "hr/offers/list";
    }

    @GetMapping("/create")
    public String createForm(@RequestParam String candidateId, Model model) {
        model.addAttribute("candidateId", candidateId);
        model.addAttribute("offer", new Offer());
        return "hr/offers/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam String candidateId, @ModelAttribute Offer offer, Model model) {
        try {
            offerService.createOffer(candidateId, offer);
            return "redirect:/hr/offers";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("candidateId", candidateId);
            return "hr/offers/form";
        }
    }
}
