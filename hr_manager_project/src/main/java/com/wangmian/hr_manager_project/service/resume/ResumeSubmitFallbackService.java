package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.config.RabbitConfig;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.service.SeekerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简历提交降级服务
 * 第一级: RabbitMQ（主）
 * 第二级: Redis List（降级）
 * 第三级: 直接 MongoDB（兜底）
 */
@Service
public class ResumeSubmitFallbackService {

    private static final Logger log = LoggerFactory.getLogger(ResumeSubmitFallbackService.class);
    private static final String REDIS_FALLBACK_KEY = "resume:fallback:queue";

    private final CandidateRepository candidateRepository;
    private final SeekerService seekerService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final List<String> pendingFallback = new CopyOnWriteArrayList<>();

    public ResumeSubmitFallbackService(CandidateRepository candidateRepository,
                                        SeekerService seekerService,
                                        StringRedisTemplate redis) {
        this.candidateRepository = candidateRepository;
        this.seekerService = seekerService;
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /** 第二级降级：写入 Redis List */
    public void submitToRedis(String msgJson) {
        redis.opsForList().leftPush(REDIS_FALLBACK_KEY, msgJson);
        redis.expire(REDIS_FALLBACK_KEY, Duration.ofHours(1));
        log.info("Resume submitted to Redis fallback queue");
    }

    /** 第三级兜底：直接写入 MongoDB */
    public void submitDirect(Map<String, Object> msg) {
        try {
            String seekerId = (String) msg.get("seekerId");
            String position = (String) msg.get("position");
            String candidateJson = (String) msg.get("candidateJson");
            Candidate candidate = mapper.readValue(candidateJson, Candidate.class);
            candidate.setCreatedAt(LocalDateTime.now());
            candidate.setUpdatedAt(LocalDateTime.now());

            Candidate saved = candidateRepository.save(candidate);
            seekerService.linkCandidate(seekerId, saved.getId(), position);
            log.info("Resume saved directly: {} for {}", candidate.getName(), position);
        } catch (Exception e) {
            log.error("Direct resume save failed", e);
        }
    }

    /** 定时消费 Redis 降级队列 */
    @Scheduled(fixedDelay = 5000)
    public void consumeRedisFallback() {
        try {
            String json = redis.opsForList().rightPop(REDIS_FALLBACK_KEY);
            while (json != null) {
                pendingFallback.add(json);
                if (pendingFallback.size() >= RabbitConfig.BATCH_SIZE) {
                    flushPending(pendingFallback);
                    pendingFallback.clear();
                }
                json = redis.opsForList().rightPop(REDIS_FALLBACK_KEY);
            }
            // 兜底写入剩余
            if (!pendingFallback.isEmpty()) {
                flushPending(pendingFallback);
                pendingFallback.clear();
            }
        } catch (Exception e) {
            log.debug("Redis fallback consume skipped: {}", e.getMessage());
        }
    }

    private void flushPending(List<String> entries) {
        int count = 0;
        for (String json : entries) {
            try {
                Map msg = mapper.readValue(json, Map.class);
                String seekerId = (String) msg.get("seekerId");
                String position = (String) msg.get("position");
                String candidateJson = (String) msg.get("candidateJson");
                Candidate candidate = mapper.readValue(candidateJson, Candidate.class);
                candidate.setCreatedAt(LocalDateTime.now());
                candidate.setUpdatedAt(LocalDateTime.now());
                Candidate saved = candidateRepository.save(candidate);
                seekerService.linkCandidate(seekerId, saved.getId(), position);
                count++;
            } catch (Exception e) {
                log.error("Fallback flush entry failed", e);
            }
        }
        log.info("Flushed {} fallback entries", count);
    }
}
