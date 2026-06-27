package com.wangmian.hr_manager_project.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.agent.AgentRegistry;
import com.wangmian.hr_manager_project.config.RabbitConfig;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.event.InterviewStatusEvent;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Seeker;
import com.wangmian.hr_manager_project.model.dto.ApiResponse;
import com.wangmian.hr_manager_project.model.enums.InterviewStatus;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.OfferService;
import com.wangmian.hr_manager_project.service.SeekerService;
import com.wangmian.hr_manager_project.service.InterviewNotificationService;
import com.wangmian.hr_manager_project.service.resume.ResumeSubmitFallbackService;
import com.wangmian.hr_manager_project.service.resume.ResumeTextExtractionService;
import com.wangmian.hr_manager_project.service.resume.ResumeValidationService;
import com.wangmian.hr_manager_project.util.FileStorageUtil;
import org.slf4j.Logger;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final ResumeTextExtractionService textExtractionService;
    private final AgentRegistry agentRegistry;
    private final FileStorageUtil fileStorageUtil;
    private final CandidateRepository candidateRepository;
    private final InterviewRecordRepository interviewRepository;
    private final OfferService offerService;
    private final RabbitTemplate rabbitTemplate;
    private final ResumeSubmitFallbackService fallbackService;
    private final InterviewNotificationService interviewNotificationService;
    private final ObjectMapper mapper;

    public SeekerApiController(SeekerService seekerService, CandidateService candidateService,
                               ResumeValidationService resumeValidationService,
                               ResumeTextExtractionService textExtractionService,
                               AgentRegistry agentRegistry,
                               FileStorageUtil fileStorageUtil,
                               CandidateRepository candidateRepository, InterviewRecordRepository interviewRepository,
                               OfferService offerService,
                               RabbitTemplate rabbitTemplate,
                               ResumeSubmitFallbackService fallbackService,
                               InterviewNotificationService interviewNotificationService) {
        this.seekerService = seekerService;
        this.candidateService = candidateService;
        this.resumeValidationService = resumeValidationService;
        this.textExtractionService = textExtractionService;
        this.agentRegistry = agentRegistry;
        this.fileStorageUtil = fileStorageUtil;
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
        this.offerService = offerService;
        this.rabbitTemplate = rabbitTemplate;
        this.fallbackService = fallbackService;
        this.interviewNotificationService = interviewNotificationService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /** 获取或创建演示求职者 */
    @PostMapping("/seeker/demo")
    public ResponseEntity<ApiResponse<Seeker>> demoLogin() {
        try {
            Seeker seeker = seekerService.registerOrGet("demo", "演示用户", "demo@example.com", "13800138000");
            return ResponseEntity.ok(ApiResponse.success(seeker));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 获取求职者完整状态（返回所有岗位投递列表） */
    @GetMapping("/seeker/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable String id) {
        Optional<Seeker> seekerOpt = seekerService.findById(id);
        if (seekerOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("求职者不存在"));
        }
        Seeker seeker = seekerOpt.get();

        // 返回该求职者所有岗位的候选人
        List<Candidate> candidates = candidateRepository.findBySeekerId(id);
        List<Map<String, Object>> candidateDetails = new ArrayList<>();
        for (Candidate c : candidates) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("candidate", c);
            detail.put("interviews", interviewRepository.findByCandidateIdOrderByRoundAsc(c.getId()));
            detail.put("offer", offerService.findByCandidateId(c.getId()).orElse(null));
            candidateDetails.add(detail);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seeker", seeker);
        data.put("candidates", candidateDetails);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 检查能否投递指定岗位 */
    @GetMapping("/seeker/{id}/can-submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> canSubmit(
            @PathVariable String id, @RequestParam(required = false) String position) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (position != null && !position.isBlank()) {
            result.put("canSubmit", seekerService.canSubmit(id, position));
        } else {
            result.put("canSubmit", true);
        }
        result.put("seekerId", id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 响应面试邀约 */
    @PutMapping("/interview/{id}/respond")
    public ResponseEntity<ApiResponse<String>> respondInterview(
            @PathVariable String id, @RequestParam boolean accept) {
        try {
            InterviewRecord interview = interviewRepository.findById(id).orElseThrow();
            Candidate candidate = candidateRepository.findById(interview.getCandidateId()).orElseThrow();
            com.wangmian.hr_manager_project.model.enums.CandidateStatus newStatus =
                    accept ? com.wangmian.hr_manager_project.model.enums.CandidateStatus.IN_INTERVIEW
                           : com.wangmian.hr_manager_project.model.enums.CandidateStatus.REJECTED;
            candidateService.updateStatus(interview.getCandidateId(), newStatus, "SEEKER",
                    accept ? "求职者接受面试邀约" : "求职者拒绝面试邀约");

            // 更新面试记录状态
            if (accept) {
                interview.setInterviewStatus(InterviewStatus.ACCEPTED);
                interviewRepository.save(interview);
            }

            // 异步通知HR
            interviewNotificationService.publishEvent(new InterviewStatusEvent(
                    accept ? "ACCEPTED" : "REJECTED",
                    candidate.getId(), candidate.getName(), candidate.getPosition()));

            return ResponseEntity.ok(ApiResponse.success(accept ? "已接受面试邀约" : "已拒绝面试邀约"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("操作失败: " + e.getMessage()));
        }
    }

    /** 同步简历解析：上传 → 保存文件 → PDF提取 → AI解析 → 返回解析结果 + 文件信息 */
    @PostMapping("/resume/parse-sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parseResumeSync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("seekerId") String seekerId,
            @RequestParam("position") String position) {
        try {
            if (!seekerService.canSubmit(seekerId, position)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("该岗位已有进行中的申请，无法重复投递"));
            }
            resumeValidationService.validate(file);
            String storedName = fileStorageUtil.saveFile(file);

            // PDF提取文本
            String text = textExtractionService.extractText(
                    fileStorageUtil.loadFile(storedName).getFile()
            );

            // AI解析
            @SuppressWarnings("unchecked")
            Candidate candidate = agentRegistry.execute("resume-parse", text);
            if (candidate == null) {
                candidate = new Candidate();
            }
            candidate.setSeekerId(seekerId);
            if (position != null && !position.isBlank()) {
                candidate.setPosition(position);
            }
            candidate.setResumeFileName(file.getOriginalFilename());
            candidate.setResumeFilePath(storedName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("storedFileName", storedName);
            result.put("resumeFileName", file.getOriginalFilename());
            result.put("candidate", candidate);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Parse sync failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("解析失败: " + e.getMessage()));
        }
    }

    /** 提交完整候选人信息 → RabbitMQ 异步落盘 */
    @PostMapping("/resume/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitCandidate(@RequestBody Map<String, Object> body) {
        try {
            String seekerId = (String) body.get("seekerId");
            String storedFileName = (String) body.get("storedFileName");
            String resumeFileName = (String) body.get("resumeFileName");

            @SuppressWarnings("unchecked")
            Map<String, Object> candidateMap = (Map<String, Object>) body.get("candidate");
            Candidate candidate = mapper.convertValue(candidateMap, Candidate.class);
            String position = candidate.getPosition();

            if (seekerId == null || !seekerService.canSubmit(seekerId, position)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("该岗位已有进行中的申请，无法重复投递"));
            }

            if (candidate.getName() == null || candidate.getName().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("姓名不能为空"));
            }
            if (candidate.getPhone() == null || candidate.getPhone().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("电话不能为空"));
            }
            if (position == null || position.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("应聘岗位不能为空"));
            }

            candidate.setSeekerId(seekerId);
            candidate.setResumeFileName(resumeFileName);
            candidate.setResumeFilePath(storedFileName);

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("seekerId", seekerId);
            msg.put("position", position);
            msg.put("candidateJson", mapper.writeValueAsString(candidate));
            String msgJson = mapper.writeValueAsString(msg);

            // 第一级：RabbitMQ
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.RESUME_EXCHANGE, RabbitConfig.RESUME_KEY, msgJson);
                log.info("Resume submitted via MQ: {} for {}", candidate.getName(), position);
            } catch (Exception e1) {
                log.warn("MQ unavailable, falling back to Redis: {}", e1.getMessage());
                // 第二级：Redis List 降级
                try {
                    fallbackService.submitToRedis(msgJson);
                    log.info("Resume submitted via Redis fallback: {} for {}", candidate.getName(), position);
                } catch (Exception e2) {
                    log.warn("Redis unavailable, falling back to direct DB: {}", e2.getMessage());
                    // 第三级：直接 MongoDB
                    fallbackService.submitDirect(msg);
                    log.info("Resume saved directly: {} for {}", candidate.getName(), position);
                }
            }

            return ResponseEntity.ok(ApiResponse.success(Map.of("status", "submitted", "position", position)));
        } catch (Exception e) {
            log.error("Submit candidate failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("提交失败: " + e.getMessage()));
        }
    }

    /** 求职者更新/补充自己的候选人信息 */
    @PutMapping("/seeker/candidate/{candidateId}")
    public ResponseEntity<ApiResponse<Candidate>> updateCandidate(
            @PathVariable String candidateId, @RequestBody Candidate updated) {
        try {
            Candidate existing = candidateRepository.findById(candidateId).orElse(null);
            if (existing == null) {
                return ResponseEntity.status(404).body(ApiResponse.error("候选人信息不存在"));
            }
            // 只允许更新基本信息字段，不允许变更状态
            if (updated.getName() != null) existing.setName(updated.getName());
            if (updated.getEmail() != null) existing.setEmail(updated.getEmail());
            if (updated.getPhone() != null) existing.setPhone(updated.getPhone());
            if (updated.getPosition() != null) existing.setPosition(updated.getPosition());
            if (updated.getYearsOfExperience() != null) existing.setYearsOfExperience(updated.getYearsOfExperience());
            if (updated.getIsFreshGraduate() != null) existing.setIsFreshGraduate(updated.getIsFreshGraduate());
            if (updated.getGraduationYear() != null) existing.setGraduationYear(updated.getGraduationYear());
            if (updated.getEducationLevel() != null) existing.setEducationLevel(updated.getEducationLevel());
            if (updated.getSchool() != null) existing.setSchool(updated.getSchool());
            if (updated.getMajor() != null) existing.setMajor(updated.getMajor());
            if (updated.getTechStack() != null) existing.setTechStack(updated.getTechStack());
            if (updated.getWorkHistory() != null) existing.setWorkHistory(updated.getWorkHistory());
            if (updated.getSelfEvaluation() != null) existing.setSelfEvaluation(updated.getSelfEvaluation());
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            Candidate saved = candidateRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("更新失败: " + e.getMessage()));
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
