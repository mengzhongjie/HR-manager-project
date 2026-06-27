package com.wangmian.hr_manager_project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.config.RabbitConfig;
import com.wangmian.hr_manager_project.model.event.InterviewStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 面试状态变更通知服务（三级降级）
 * 1️⃣ RabbitMQ → @RabbitListener → SSE → HR端
 * 2️⃣ Redis List（MQ降级）
 * 3️⃣ 直接广播 SSE（状态已在MongoDB中生效）
 */
@Service
public class InterviewNotificationService {

    private static final Logger log = LoggerFactory.getLogger(InterviewNotificationService.class);
    private static final String REDIS_FALLBACK_KEY = "interview:status:queue";
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;
    private final PositionSseService sseService;
    private final ObjectMapper mapper;

    public InterviewNotificationService(RabbitTemplate rabbitTemplate,
                                         StringRedisTemplate redis,
                                         PositionSseService sseService) {
        this.rabbitTemplate = rabbitTemplate;
        this.redis = redis;
        this.sseService = sseService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /** 发布面试状态变更事件（三级降级） */
    public void publishEvent(InterviewStatusEvent event) {
        try {
            String json = mapper.writeValueAsString(event);

            // 第一级：RabbitMQ
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.INTERVIEW_EXCHANGE,
                        RabbitConfig.INTERVIEW_KEY, json);
                log.info("Interview event published to MQ: {} - {}", event.getAction(), event.getCandidateName());
                return;
            } catch (Exception e1) {
                log.warn("MQ unavailable, falling back to Redis: {}", e1.getMessage());
            }

            // 第二级：Redis List 降级
            try {
                redis.opsForList().leftPush(REDIS_FALLBACK_KEY, json);
                redis.expire(REDIS_FALLBACK_KEY, REDIS_TTL);
                log.info("Interview event saved to Redis fallback: {}", event.getCandidateName());
                return;
            } catch (Exception e2) {
                log.warn("Redis unavailable, direct SSE broadcast: {}", e2.getMessage());
            }

            // 第三级：直接 SSE 广播（状态已在DB中，HR刷新即见）
            sseService.broadcastInterview(json);
            log.info("Interview event broadcast via SSE: {}", event.getCandidateName());

        } catch (Exception e) {
            log.error("Failed to publish interview event", e);
        }
    }

    /** RabbitMQ 消费者：收到面试事件 → SSE 广播给 HR 端 */
    @RabbitListener(queues = "hr.interview.queue")
    public void onInterviewEvent(String message) {
        log.debug("Interview event received from MQ: {}", message);
        sseService.broadcastInterview(message);
    }

    /** 消费 Redis 降级队列中的面试事件 */
    public void consumeRedisFallback() {
        try {
            String json = redis.opsForList().rightPop(REDIS_FALLBACK_KEY);
            while (json != null) {
                sseService.broadcastInterview(json);
                json = redis.opsForList().rightPop(REDIS_FALLBACK_KEY);
            }
        } catch (Exception e) {
            log.debug("Redis fallback consume skipped: {}", e.getMessage());
        }
    }
}
