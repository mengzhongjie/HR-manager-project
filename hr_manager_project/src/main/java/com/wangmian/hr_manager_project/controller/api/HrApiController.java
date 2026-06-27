package com.wangmian.hr_manager_project.controller.api;

import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.model.document.InterviewRecord;
import com.wangmian.hr_manager_project.model.document.Offer;
import com.wangmian.hr_manager_project.model.document.Onboarding;
import com.wangmian.hr_manager_project.model.dto.ApiResponse;
import com.wangmian.hr_manager_project.model.dto.CandidateFilterDTO;
import com.wangmian.hr_manager_project.model.enums.CandidateStatus;
import com.wangmian.hr_manager_project.service.CandidateService;
import com.wangmian.hr_manager_project.service.InterviewService;
import com.wangmian.hr_manager_project.service.OfferService;
import com.wangmian.hr_manager_project.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr")
public class HrApiController {

    private final CandidateService candidateService;
    private final InterviewService interviewService;
    private final OfferService offerService;
    private final OnboardingService onboardingService;

    public HrApiController(CandidateService candidateService, InterviewService interviewService,
                           OfferService offerService, OnboardingService onboardingService) {
        this.candidateService = candidateService;
        this.interviewService = interviewService;
        this.offerService = offerService;
        this.onboardingService = onboardingService;
    }

    // ========== 仪表盘 ==========

    /** 所有岗位列表 */
    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<String>>> getPositions() {
        return ResponseEntity.ok(ApiResponse.success(candidateService.getAllPositions()));
    }

    /** 岗位候选人统计 */
    @GetMapping("/positions/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPositionStats() {
        List<String> positions = candidateService.getAllPositions();
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
}
