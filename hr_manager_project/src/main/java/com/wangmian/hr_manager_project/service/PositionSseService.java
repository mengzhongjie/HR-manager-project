package com.wangmian.hr_manager_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PositionSseService {

    private static final Logger log = LoggerFactory.getLogger(PositionSseService.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 客户端订阅 SSE */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // 发送初始连接确认
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        log.info("SSE client connected, total: {}", emitters.size());
        return emitter;
    }

    /** 向所有连接的客户端广播岗位变更事件 */
    public void broadcast(String eventJson) {
        broadcastEvent("position-change", eventJson);
    }

    /** 向所有连接的客户端广播面试状态变更事件 */
    public void broadcastInterview(String eventJson) {
        broadcastEvent("interview-status", eventJson);
    }

    /** 通用广播 */
    private void broadcastEvent(String eventName, String eventJson) {
        if (emitters.isEmpty()) return;

        Iterator<SseEmitter> it = emitters.iterator();
        while (it.hasNext()) {
            SseEmitter emitter = it.next();
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(eventJson));
            } catch (IOException e) {
                emitter.completeWithError(e);
                it.remove();
                log.debug("Removed stale SSE emitter");
            }
        }
    }

    /** RabbitMQ 监听：收到岗位变更消息后广播给所有 SSE 客户端 */
    @RabbitListener(queues = "hr.position.queue")
    public void onPositionChange(String message) {
        log.debug("RabbitMQ received position change: {}", message);
        broadcast(message);
    }

    @PreDestroy
    public void cleanup() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    /** 定时清理过期 emitter（由外部 scheduled 调用） */
    public void purgeStaleEmitters() {
        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                return false;
            } catch (IOException e) {
                return true;
            }
        });
    }
}
