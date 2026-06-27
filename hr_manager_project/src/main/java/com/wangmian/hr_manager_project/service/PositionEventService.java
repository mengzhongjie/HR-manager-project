package com.wangmian.hr_manager_project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.config.RabbitConfig;
import com.wangmian.hr_manager_project.model.event.PositionChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PositionEventService {

    private static final Logger log = LoggerFactory.getLogger(PositionEventService.class);

    private final RabbitTemplate rabbitTemplate;
    private final PositionCacheService positionCacheService;
    private final ObjectMapper mapper;

    public PositionEventService(RabbitTemplate rabbitTemplate, PositionCacheService positionCacheService) {
        this.rabbitTemplate = rabbitTemplate;
        this.positionCacheService = positionCacheService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /** 异步处理：清除缓存 → 通过 RabbitMQ 广播通知其他实例 */
    @Async("positionEventExecutor")
    @EventListener
    public void handlePositionChange(PositionChangeEvent event) {
        log.info("Position change event: {} - {} ({})", event.getAction(), event.getPositionName(), event.getPositionId());

        // 1. 清除本地缓存
        positionCacheService.evictPositionCache();

        // 2. 通过 RabbitMQ 广播
        try {
            String json = mapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(RabbitConfig.POSITION_EXCHANGE, RabbitConfig.POSITION_KEY, json);
            log.debug("Published to RabbitMQ: {}", event.getAction());
        } catch (Exception e) {
            log.error("Failed to publish position event to RabbitMQ", e);
        }
    }
}
