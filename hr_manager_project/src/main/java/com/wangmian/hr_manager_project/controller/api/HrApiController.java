package com.wangmian.hr_manager_project.controller.api;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Onboarding;
import com.wangmian.hr_manager_project.model.document.Position;
import com.wangmian.hr_manager_project.model.dto.ApiResponse;
import com.wangmian.hr_manager_project.model.dto.CandidateFilterDTO;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.model.enums.InterviewResult;
import com.wangmian.hr_manager_project.model.enums.InterviewRound;
import com.wangmian.hr_manager_project.model.enums.InterviewStatus;
import com.wangmian.hr_manager_project.model.event.PositionChangeEvent;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.repository.InterviewRecordRepository;
import com.wangmian.hr_manager_project.repository.PositionRepository;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.InterviewService;
import com.wangmian.hr_manager_project.service.OfferService;
import com.wangmian.hr_manager_project.service.OnboardingService;
import com.wangmian.hr_manager_project.service.PositionCacheService;
import com.wangmian.hr_manager_project.service.PositionEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr")
public class HrApiController {

    private static final Logger log = LoggerFactory.getLogger(HrApiController.class);

    private final CandidateService candidateService;
    private final InterviewService interviewService;
    private final OfferService offerService;
    private final OnboardingService onboardingService;
    private final PositionRepository positionRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRecordRepository interviewRecordRepository;
    private final PositionCacheService positionCacheService;
    private final PositionEventService positionEventService;

    public HrApiController(CandidateService candidateService, InterviewService interviewService,
                           OfferService offerService, OnboardingService onboardingService,
                           PositionRepository positionRepository, CandidateRepository candidateRepository,
                           InterviewRecordRepository interviewRecordRepository,
                           PositionCacheService positionCacheService, PositionEventService positionEventService) {
        this.candidateService = candidateService;
        this.interviewService = interviewService;
        this.offerService = offerService;
        this.onboardingService = onboardingService;
        this.positionRepository = positionRepository;
        this.candidateRepository = candidateRepository;
        this.interviewRecordRepository = interviewRecordRepository;
        this.positionCacheService = positionCacheService;
        this.positionEventService = positionEventService;
    }

    // ========== 仪表盘 ==========

    /** 所有岗位列表 */
    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<String>>> getPositions() {
        return ResponseEntity.ok(ApiResponse.success(candidateService.getAllPositions()));
    }

    /** 岗位候选人统计（从 Position 集合读取岗位列表，同步岗位管理） */
    @GetMapping("/positions/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPositionStats() {
        List<String> positions = positionRepository.findAll().stream()
                .map(Position::getName)
                .filter(p -> p != null && !p.isEmpty())
                .toList();
        Map<String, Long> countMap = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Long>> statusMap = new java.util.LinkedHashMap<>();
        for (String pos : positions) {
            countMap.put(pos, candidateService.countByPosition(pos));
            statusMap.put(pos, candidateService.getStatusCountByPosition(pos));
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("positions", positions);
        data.put("counts", countMap);
        data.put("statusCounts", statusMap);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ========== 候选人管理 ==========

    /** 按岗位候选人列表（含筛选） */
    @GetMapping("/positions/{position}/candidates")
    public ResponseEntity<ApiResponse<List<Candidate>>> listCandidates(
            @PathVariable String position, CandidateFilterDTO filter) {
        return ResponseEntity.ok(ApiResponse.success(candidateService.filterByPosition(position, filter)));
    }

    /** 候选人详情 */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<Candidate>> getCandidate(@PathVariable String id) {
        return candidateService.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success(c)))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error("候选人不存在")));
    }

    /** 更新候选人状态 */
    @PutMapping("/candidates/{id}/status")
    public ResponseEntity<ApiResponse<Candidate>> updateStatus(
            @PathVariable String id,
            @RequestParam CandidateStatus status,
            @RequestParam(defaultValue = "HR") String actor,
            @RequestParam(defaultValue = "") String reason) {
        try {
            Candidate updated = candidateService.updateStatus(id, status, actor,
                    reason.isEmpty() ? "HR手动操作" : reason);
            return ResponseEntity.ok(ApiResponse.success(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 触发AI资质评定 */
    @PostMapping("/candidates/{id}/ai-qualify")
    public ResponseEntity<ApiResponse<String>> aiQualify(@PathVariable String id) {
        try {
            candidateService.runAiQualify(id);
            return ResponseEntity.ok(ApiResponse.success("AI资质评定完成"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("AI评定失败: " + e.getMessage()));
        }
    }

    /** 发送面试邀请（含预约面试时间） */
    @PostMapping("/candidates/{id}/invite-interview")
    public ResponseEntity<ApiResponse<InterviewRecord>> inviteInterview(
            @PathVariable String id,
            @RequestParam(required = false) String interviewDate) {
        try {
            Candidate candidate = candidateRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("候选人不存在"));

            // 自动确定下一轮
            List<InterviewRecord> existing = interviewRecordRepository.findByCandidateIdOrderByRoundAsc(id);
            InterviewRound nextRound = InterviewRound.ROUND_1;
            if (existing.stream().anyMatch(r -> r.getRound() == InterviewRound.ROUND_1 && r.getResult() == InterviewResult.PASSED)) {
                nextRound = InterviewRound.ROUND_2;
            }
            if (existing.stream().anyMatch(r -> r.getRound() == InterviewRound.ROUND_2 && r.getResult() == InterviewResult.PASSED)) {
                nextRound = InterviewRound.ROUND_3;
            }

            // 更新候选人状态
            candidateService.updateStatus(id, CandidateStatus.INTERVIEW_INVITED, "HR",
                    "发送第" + switch (nextRound) {
                        case ROUND_1 -> "一";
                        case ROUND_2 -> "二";
                        case ROUND_3 -> "三";
                    } + "轮面试邀请");

            // 创建面试记录（interviewStatus=PENDING，不设result，面试结束后才填）
            InterviewRecord record = new InterviewRecord();
            record.setCandidateId(id);
            record.setCandidateName(candidate.getName());
            record.setCandidatePosition(candidate.getPosition());
            record.setRound(nextRound);
            record.setInterviewStatus(InterviewStatus.PENDING);
            if (interviewDate != null && !interviewDate.isBlank()) {
                java.time.LocalDate date = java.time.LocalDate.parse(interviewDate);
                if (!date.isAfter(java.time.LocalDate.now())) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("面试日期必须在今天之后"));
                }
                record.setInterviewDate(date);
            }
            InterviewRecord saved = interviewRecordRepository.save(record);

            log.info("Interview invitation sent: {} round={} date={}", candidate.getName(), nextRound, interviewDate);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("邀请失败: " + e.getMessage()));
        }
    }

    /** 开始面试（面试官进入） */
    @PutMapping("/interviews/{id}/start")
    public ResponseEntity<ApiResponse<InterviewRecord>> startInterview(@PathVariable String id) {
        try {
            InterviewRecord record = interviewRecordRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("面试记录不存在"));
            record.setInterviewStatus(InterviewStatus.IN_PROGRESS);
            InterviewRecord saved = interviewRecordRepository.save(record);
            log.info("Interview started: {}", id);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("操作失败: " + e.getMessage()));
        }
    }

    /** 结束面试（面试官完成，填写评分/反馈/结果） */
    @PutMapping("/interviews/{id}/complete")
    public ResponseEntity<ApiResponse<InterviewRecord>> completeInterview(
            @PathVariable String id,
            @RequestParam InterviewResult result,
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) String feedback) {
        try {
            InterviewRecord record = interviewRecordRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("面试记录不存在"));
            record.setInterviewStatus(InterviewStatus.COMPLETED);
            record.setResult(result);
            if (score != null) record.setScore(score);
            if (feedback != null) record.setFeedback(feedback);
            // 面试记录先保存（评分/反馈/结果）
            InterviewRecord saved = interviewRecordRepository.save(record);

            // 推进候选人状态（通过状态机校验，失败则回滚）
            interviewService.saveInterview(saved);

            log.info("Interview completed: {} result={} score={}", id, result, score);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("操作失败: " + e.getMessage()));
        }
    }

    // ========== 备选列表 ==========

    /** 备选列表（按岗位） */
    @GetMapping("/positions/{position}/backup")
    public ResponseEntity<ApiResponse<List<Candidate>>> getBackupList(@PathVariable String position) {
        List<Candidate> backups = candidateService.findByPosition(position).stream()
                .filter(c -> c.getStatus() == CandidateStatus.PENDING_ARCHIVE)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(backups));
    }

    /** 从备选恢复 */
    @PutMapping("/candidates/{id}/restore")
    public ResponseEntity<ApiResponse<Candidate>> restoreFromBackup(@PathVariable String id) {
        try {
            Candidate restored = candidateService.updateStatus(id, CandidateStatus.NEW,
                    "HR", "从备选库恢复");
            return ResponseEntity.ok(ApiResponse.success(restored));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ========== 面试管理 ==========

    /** 面试概览 */
    @GetMapping("/interviews")
    public ResponseEntity<ApiResponse<List<InterviewRecord>>> getAllInterviews() {
        return ResponseEntity.ok(ApiResponse.success(interviewService.getAllInterviews()));
    }

    /** 候选人面试记录 */
    @GetMapping("/candidates/{candidateId}/interviews")
    public ResponseEntity<ApiResponse<List<InterviewRecord>>> getCandidateInterviews(
            @PathVariable String candidateId) {
        return ResponseEntity.ok(ApiResponse.success(interviewService.getByCandidateId(candidateId)));
    }

    /** 创建面试记录 */
    @PostMapping("/interviews")
    public ResponseEntity<ApiResponse<InterviewRecord>> createInterview(@RequestBody InterviewRecord interview) {
        try {
            InterviewRecord saved = interviewService.saveInterview(interview);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ========== Offer管理 ==========

    /** 待发Offer列表（WAITING_OFFER状态候选人） */
    @GetMapping("/offers/pending")
    public ResponseEntity<ApiResponse<List<Candidate>>> getPendingOffers() {
        List<Candidate> pending = candidateService.filterByStatus(CandidateStatus.WAITING_OFFER);
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    /** 创建Offer */
    @PostMapping("/offers")
    public ResponseEntity<ApiResponse<Offer>> createOffer(
            @RequestParam String candidateId, @RequestBody Offer offer) {
        try {
            Offer saved = offerService.createOffer(candidateId, offer);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Offer列表 */
    @GetMapping("/offers")
    public ResponseEntity<ApiResponse<List<Offer>>> getAllOffers() {
        return ResponseEntity.ok(ApiResponse.success(offerService.findAll()));
    }

    /** 候选人Offer */
    @GetMapping("/candidates/{candidateId}/offer")
    public ResponseEntity<ApiResponse<Offer>> getCandidateOffer(@PathVariable String candidateId) {
        return offerService.findByCandidateId(candidateId)
                .map(o -> ResponseEntity.ok(ApiResponse.success(o)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    // ========== 入职管理 ==========

    /** 创建入职登记 */
    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<Onboarding>> createOnboarding(
            @RequestParam String offerId, @RequestBody Onboarding onboarding) {
        try {
            Onboarding saved = onboardingService.createOnboarding(offerId, onboarding);
            return ResponseEntity.ok(ApiResponse.success(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 候选人入职信息 */
    @GetMapping("/candidates/{candidateId}/onboarding")
    public ResponseEntity<ApiResponse<Onboarding>> getCandidateOnboarding(
            @PathVariable String candidateId) {
        return onboardingService.findByCandidateId(candidateId)
                .map(o -> ResponseEntity.ok(ApiResponse.success(o)))
                .orElse(ResponseEntity.ok(ApiResponse.success(null)));
    }

    // ========== 岗位管理（CRUD） ==========

    /** 岗位列表（旁路缓存） */
    @GetMapping("/positions/list")
    public ResponseEntity<ApiResponse<List<Position>>> listPositions() {
        return ResponseEntity.ok(ApiResponse.success(positionCacheService.getPositions(
                () -> positionRepository.findAll())));
    }

    /** 创建岗位 */
    @PostMapping("/positions")
    public ResponseEntity<ApiResponse<Position>> createPosition(@RequestBody Position position) {
        if (position.getName() == null || position.getName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("岗位名称不能为空"));
        }
        if (positionRepository.existsByName(position.getName())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("岗位名称已存在"));
        }
        position.setCreatedAt(java.time.LocalDateTime.now());
        position.setUpdatedAt(java.time.LocalDateTime.now());
        Position saved = positionRepository.save(position);
        positionEventService.handlePositionChange(
                new PositionChangeEvent("CREATED", saved.getId(), saved.getName()));
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    /** 更新岗位 */
    @PutMapping("/positions/{id}")
    public ResponseEntity<ApiResponse<Position>> updatePosition(
            @PathVariable String id, @RequestBody Position position) {
        Position existing = positionRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("岗位不存在"));
        }
        if (position.getName() != null && !position.getName().isBlank()) {
            existing.setName(position.getName());
        }
        if (position.getDescription() != null) existing.setDescription(position.getDescription());
        if (position.getDepartment() != null) existing.setDepartment(position.getDepartment());
        if (position.getRequirements() != null) existing.setRequirements(position.getRequirements());
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        Position updated = positionRepository.save(existing);
        positionEventService.handlePositionChange(
                new PositionChangeEvent("UPDATED", id, updated.getName()));
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /** 删除岗位 */
    @DeleteMapping("/positions/{id}")
    public ResponseEntity<ApiResponse<String>> deletePosition(@PathVariable String id) {
        Position position = positionRepository.findById(id).orElse(null);
        if (position == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("岗位不存在"));
        }
        // 检查是否有关联候选人
        long count = candidateRepository.countByPosition(position.getName());
        if (count > 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "该岗位下有 " + count + " 名候选人，请先处理后再删除"));
        }
        positionRepository.deleteById(id);
        positionEventService.handlePositionChange(
                new PositionChangeEvent("DELETED", id, position.getName()));
        return ResponseEntity.ok(ApiResponse.success("删除成功"));
    }
}
