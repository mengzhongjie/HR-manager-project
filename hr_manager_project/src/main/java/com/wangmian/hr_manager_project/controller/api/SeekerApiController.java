package com.wangmian.hr_manager_project.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Seeker;
import com.wangmian.hr_manager_project.model.dto.ApiResponse;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.OfferService;
import com.wangmian.hr_manager_project.service.SeekerService;
import com.wangmian.hr_manager_project.service.resume.ResumeParseQueueService;
import com.wangmian.hr_manager_project.service.resume.ResumeValidationService;
import com.wangmian.hr_manager_project.util.FileStorageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api")
public class SeekerApiController {

    private static final Logger log = LoggerFactory.getLogger(SeekerApiController.class);

    private final SeekerService seekerService;
    private final CandidateService candidateService;
    private final ResumeValidationService resumeValidationService;
    private final ResumeParseQueueService parseQueueService;
    private final FileStorageUtil fileStorageUtil;
    private final CandidateRepository candidateRepository;
    private final InterviewRecordRepository interviewRepository;
    private final OfferService offerService;
    private final ObjectMapper mapper;

    public SeekerApiController(SeekerService seekerService, CandidateService candidateService,
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

    /** 求职者登录/注册 */
    @PostMapping("/seeker/login")
    public ResponseEntity<ApiResponse<Seeker>> login(@RequestParam String username,
                                                      @RequestParam String name,
                                                      @RequestParam String email,
                                                      @RequestParam String phone) {
        try {
            Seeker seeker = seekerService.registerOrGet(username, name, email, phone);
            return ResponseEntity.ok(ApiResponse.success(seeker));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 获取求职者完整状态 */
    @GetMapping("/seeker/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable String id) {
        Optional<Seeker> seekerOpt = seekerService.findById(id);
        if (seekerOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("求职者不存在"));
        }
        Seeker seeker = seekerOpt.get();
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seeker", seeker);
        data.put("candidate", candidate);
        data.put("interviews", interviews);
        data.put("offer", offer);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 检查能否投递 */
    @GetMapping("/seeker/{id}/can-submit")
    public ResponseEntity<ApiResponse<Boolean>> canSubmit(@PathVariable String id) {
        boolean canSubmit = seekerService.canSubmit(id);
        return ResponseEntity.ok(ApiResponse.success(canSubmit));
    }

    /** 上传简历 */
    @PostMapping("/resume/upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam("seekerId") String seekerId) {
        if (!seekerService.canSubmit(seekerId)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("您当前有一份简历正在处理中，无法再次投递"));
        }
        try {
            resumeValidationService.validate(file);
            String storedName = fileStorageUtil.saveFile(file);
            Map<String, Object> task = new HashMap<>();
            task.put("seekerId", seekerId);
            task.put("resumeFileName", file.getOriginalFilename());
            task.put("storedFileName", storedName);
            String taskJson = mapper.writeValueAsString(task);
            parseQueueService.pushParseTask(taskJson);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("storedFileName", storedName);
            result.put("message", "简历上传成功，正在解析中");
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("上传失败: " + e.getMessage()));
        }
    }

    /** 响应面试邀约 */
    @PutMapping("/interview/{id}/respond")
    public ResponseEntity<ApiResponse<String>> respondInterview(
            @PathVariable String id, @RequestParam boolean accept) {
        try {
            InterviewRecord interview = interviewRepository.findById(id).orElseThrow();
            com.wangmian.hr_manager_project.model.enums.CandidateStatus newStatus =
                    accept ? com.wangmian.hr_manager_project.model.enums.CandidateStatus.IN_INTERVIEW
                           : com.wangmian.hr_manager_project.model.enums.CandidateStatus.REJECTED;
            candidateService.updateStatus(interview.getCandidateId(), newStatus, "SEEKER",
                    accept ? "求职者接受面试邀约" : "求职者拒绝面试邀约");
            return ResponseEntity.ok(ApiResponse.success(accept ? "已接受面试邀约" : "已拒绝面试邀约"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("操作失败: " + e.getMessage()));
        }
    }

    /** 响应Offer */
    @PutMapping("/offer/{id}/respond")
    public ResponseEntity<ApiResponse<String>> respondOffer(
            @PathVariable String id, @RequestParam boolean accept) {
        try {
            offerService.respondToOffer(id, accept, "SEEKER");
            return ResponseEntity.ok(ApiResponse.success(accept ? "已接受Offer" : "已拒绝Offer"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("操作失败: " + e.getMessage()));
        }
    }
}
