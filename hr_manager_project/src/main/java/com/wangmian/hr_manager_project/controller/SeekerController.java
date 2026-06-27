package com.wangmian.hr_manager_project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Seeker;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import com.wangmian.hr_manager_project.service.OfferService;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.SeekerService;
import com.wangmian.hr_manager_project.service.resume.ResumeParseQueueService;
import com.wangmian.hr_manager_project.service.resume.ResumeValidationService;
import com.wangmian.hr_manager_project.util.FileStorageUtil;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/seeker")
public class SeekerController {

    private static final Logger log = LoggerFactory.getLogger(SeekerController.class);

    private final SeekerService seekerService;
    private final CandidateService candidateService;
    private final ResumeValidationService resumeValidationService;
    private final ResumeParseQueueService parseQueueService;
    private final FileStorageUtil fileStorageUtil;
    private final CandidateRepository candidateRepository;
    private final InterviewRecordRepository interviewRepository;
    private final OfferService offerService;
    private final ObjectMapper mapper;

    public SeekerController(SeekerService seekerService, CandidateService candidateService,
                            ResumeValidationService resumeValidationService,
                            ResumeParseQueueService parseQueueService, FileStorageUtil fileStorageUtil,
                            CandidateRepository candidateRepository, InterviewRecordRepository interviewRepository,
                            OfferService offerService) {
        this.seekerService = seekerService;
        this.candidateService = candidateService;
        this.resumeValidationService = resumeValidationService;
        this.parseQueueService = parseQueueService;
        this.fileStorageUtil = fileStorageUtil;
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
        this.offerService = offerService;
        this.mapper = new ObjectMapper();
    }

    @GetMapping("")
    public String index(HttpSession session, Model model) {
        if (session.getAttribute("seekerId") != null) {
            return "redirect:/seeker/status";
        }
        return "seeker/index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String name,
                        @RequestParam String email, @RequestParam String phone,
                        HttpSession session) {
        Seeker seeker = seekerService.registerOrGet(username, name, email, phone);
        session.setAttribute("seekerId", seeker.getId());
        session.setAttribute("seekerName", seeker.getName());
        return "redirect:/seeker/upload";
    }

    @GetMapping("/upload")
    public String uploadPage(HttpSession session, Model model) {
        if (session.getAttribute("seekerId") == null) return "redirect:/seeker";
        String seekerId = (String) session.getAttribute("seekerId");
        boolean canSubmit = seekerService.canSubmit(seekerId);
        model.addAttribute("canSubmit", canSubmit);
        if (!canSubmit) {
            model.addAttribute("message", "您当前有一份简历正在被HR处理中，请等待处理结果后再投递");
        }
        return "seeker/upload";
    }

    @PostMapping("/upload")
    public String uploadResume(@RequestParam("file") MultipartFile file,
                               HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("seekerId") == null) return "redirect:/seeker";
        String seekerId = (String) session.getAttribute("seekerId");

        if (!seekerService.canSubmit(seekerId)) {
            ra.addFlashAttribute("error", "您当前有一份简历正在处理中，无法再次投递");
            return "redirect:/seeker/upload";
        }

        try {
            // Validate PDF
            resumeValidationService.validate(file);

            // Save file
            String storedName = fileStorageUtil.saveFile(file);

            // Push to Redis Stream for async parsing
            Map<String, Object> task = new HashMap<>();
            task.put("seekerId", seekerId);
            task.put("resumeFileName", file.getOriginalFilename());
            task.put("storedFileName", storedName);
            String taskJson = mapper.writeValueAsString(task);
            parseQueueService.pushParseTask(taskJson);

            ra.addFlashAttribute("success", "简历上传成功，正在解析中，请稍后查看状态");
            return "redirect:/seeker/status";
        } catch (Exception e) {
            log.error("Upload failed", e);
            ra.addFlashAttribute("error", "上传失败: " + e.getMessage());
            return "redirect:/seeker/upload";
        }
    }

    @GetMapping("/status")
    public String status(HttpSession session, Model model) {
        if (session.getAttribute("seekerId") == null) return "redirect:/seeker";
        String seekerId = (String) session.getAttribute("seekerId");
        Seeker seeker = seekerService.findById(seekerId).orElse(null);
        if (seeker == null) return "redirect:/seeker";

        Candidate candidate = null;
        List<InterviewRecord> interviews = new ArrayList<>();
        Offer offer = null;
        if (seeker.getActiveCandidateId() != null) {
            candidate = candidateRepository.findById(seeker.getActiveCandidateId()).orElse(null);
            if (candidate != null) {
                interviews = interviewRepository.findByCandidateIdOrderByRoundAsc(candidate.getId());
                offer = offerService.findByCandidateId(candidate.getId()).orElse(null);
            }
        }

        model.addAttribute("seeker", seeker);
        model.addAttribute("candidate", candidate);
        model.addAttribute("interviews", interviews);
        model.addAttribute("offer", offer);
        return "seeker/status";
    }

    @PostMapping("/interview/{id}/respond")
    public String respondInterview(@PathVariable String id, @RequestParam boolean accept,
                                   HttpSession session, RedirectAttributes ra) {
        try {
            InterviewRecord interview = interviewRepository.findById(id).orElseThrow();
            CandidateStatus newStatus = accept ? CandidateStatus.IN_INTERVIEW : CandidateStatus.REJECTED;
            candidateService.updateStatus(interview.getCandidateId(), newStatus, "SEEKER",
                    accept ? "求职者接受面试邀约" : "求职者拒绝面试邀约");
            ra.addFlashAttribute("success", accept ? "已接受面试邀约" : "已拒绝面试邀约");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败: " + e.getMessage());
        }
        return "redirect:/seeker/status";
    }

    @PostMapping("/offer/{id}/respond")
    public String respondOffer(@PathVariable String id, @RequestParam boolean accept,
                               HttpSession session, RedirectAttributes ra) {
        try {
            offerService.respondToOffer(id, accept, "SEEKER");
            ra.addFlashAttribute("success", accept ? "已接受Offer" : "已拒绝Offer");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败: " + e.getMessage());
        }
        return "redirect:/seeker/status";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/seeker";
    }
}
