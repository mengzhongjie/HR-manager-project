package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.config.RabbitConfig;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import com.wangmian.hr_manager_project.service.SeekerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RabbitMQ 消费者：积累简历提交消息 → 批量写入 MongoDB
 * 触发条件：积累 RabbitConfig.BATCH_SIZE(10) 条 或 每 RabbitConfig.BATCH_INTERVAL_MS(5秒)
 */
@Service
public class ResumeSubmitConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResumeSubmitConsumer.class);

    private final CandidateRepository candidateRepository;
    private final SeekerService seekerService;
    private final ObjectMapper mapper;
    private final List<ResumeEntry> pending = new CopyOnWriteArrayList<>();

    public ResumeSubmitConsumer(CandidateRepository candidateRepository, SeekerService seekerService) {
        this.candidateRepository = candidateRepository;
        this.seekerService = seekerService;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * 消费 RabbitMQ 简历提交消息，将消息解析后加入待处理队列。
     * 当队列积累达到批量阈值时自动触发 flush。
     *
     * @param message JSON 格式的简历提交消息，包含 seekerId、position、candidateJson
     * @throws RuntimeException 消息处理失败时抛出，由 RabbitMQ 重试机制处理
     */
    @RabbitListener(queues = "hr.resume.queue", concurrency = "1-3")
    public void onResumeSubmitted(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = mapper.readValue(message, Map.class);
            String seekerId = (String) msg.get("seekerId");
            String position = (String) msg.get("position");
            String candidateJson = (String) msg.get("candidateJson");

            Candidate candidate = mapper.readValue(candidateJson, Candidate.class);
            pending.add(new ResumeEntry(seekerId, position, candidate));

            log.debug("MQ resume queued: {} for {} (pending: {}/{})",
                    candidate.getName(), position, pending.size(), RabbitConfig.BATCH_SIZE);

            if (pending.size() >= RabbitConfig.BATCH_SIZE) {
                flush();
            }
        } catch (Exception e) {
            log.error("Failed to process resume MQ message", e);
            // 抛异常让 RabbitMQ 重试（默认3次后进入 DLQ）
            throw new RuntimeException("Resume processing failed", e);
        }
    }

    /**
     * 定时兜底刷新：当消息积累不足批量阈值时，按固定时间间隔将积压消息写入 MongoDB
     */
    @Scheduled(fixedDelayString = "${hr.resume.batch.interval-ms:5000}")
    public void scheduledFlush() {
        if (!pending.isEmpty()) {
            log.debug("Scheduled flush: {} pending entries", pending.size());
            flush();
        }
    }

    /**
     * 将待处理队列中的简历消息批量写入 MongoDB，
     * 并关联候选人到对应的求职者。
     * 写入失败时将消息重新放回待处理队列。
     *
     * @throws RuntimeException 批量保存失败时抛出，消息会回填待处理队列
     */
    public synchronized void flush() {
        if (pending.isEmpty()) return;

        List<ResumeEntry> batch = new ArrayList<>(pending);
        pending.clear();

        List<Candidate> toSave = batch.stream().map(entry -> {
            Candidate c = entry.candidate;
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        }).toList();

        try {
            List<Candidate> saved = candidateRepository.saveAll(toSave);
            log.info("Batch saved {} candidates to MongoDB", saved.size());

            for (int i = 0; i < saved.size(); i++) {
                ResumeEntry entry = batch.get(i);
                try {
                    seekerService.linkCandidate(entry.seekerId, saved.get(i).getId(), entry.position);
                } catch (Exception e) {
                    log.warn("Failed to link seeker {} to candidate {}: {}",
                            entry.seekerId, saved.get(i).getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Batch resume save failed, returning {} entries to pending queue", batch.size(), e);
            pending.addAll(batch);
            throw new RuntimeException("Batch resume save failed", e);
        }
    }

    private record ResumeEntry(String seekerId, String position, Candidate candidate) {}
}
