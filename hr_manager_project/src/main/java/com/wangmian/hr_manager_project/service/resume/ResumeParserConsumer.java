package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.agent.AgentRegistry;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.util.FileStorageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@EnableScheduling
public class ResumeParserConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserConsumer.class);

    private final ResumeParseQueueService queueService;
    private final ResumeTextExtractionService textExtractionService;
    private final ResumeBatchPersistenceService batchPersistenceService;
    private final FileStorageUtil fileStorageUtil;
    private final AgentRegistry agentRegistry;
    private final ObjectMapper mapper;

    public ResumeParserConsumer(ResumeParseQueueService queueService,
                                ResumeTextExtractionService textExtractionService,
                                ResumeBatchPersistenceService batchPersistenceService,
                                FileStorageUtil fileStorageUtil,
                                AgentRegistry agentRegistry) {
        this.queueService = queueService;
        this.textExtractionService = textExtractionService;
        this.batchPersistenceService = batchPersistenceService;
        this.fileStorageUtil = fileStorageUtil;
        this.agentRegistry = agentRegistry;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Scheduled(fixedDelay = 2000)
    public void consume() {
        StringRedisTemplate redis = queueService.getRedis();
        try {
            List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                    Consumer.from(queueService.getConsumerGroup(), queueService.getConsumerName()),
                    StreamReadOptions.empty().count(5),
                    StreamOffset.create(queueService.getStreamKey(), ReadOffset.lastConsumed())
            );

            if (messages == null || messages.isEmpty()) return;

            for (MapRecord<String, Object, Object> msg : messages) {
                try {
                    Map<Object, Object> value = msg.getValue();
                    Object payloadObj = value.get("payload");
                    if (payloadObj == null) continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> taskMap = mapper.readValue(payloadObj.toString(), Map.class);

                    String resumeFileName = (String) taskMap.get("resumeFileName");
                    String storedFileName = (String) taskMap.get("storedFileName");

                    // Extract text from saved PDF (File overload)
                    String text = textExtractionService.extractText(
                            fileStorageUtil.loadFile(storedFileName).getFile()
                    );

                    // AI Parse
                    Candidate candidate = agentRegistry.execute("resume-parse", text);
                    candidate.setResumeFileName(resumeFileName);
                    candidate.setResumeFilePath(storedFileName);

                    // Store temp result in Redis for batch persistence
                    String tempKey = "resume_temp:" + UUID.randomUUID();
                    String candidateJson = mapper.writeValueAsString(candidate);
                    redis.opsForValue().set(tempKey, candidateJson, java.time.Duration.ofMinutes(30));

                    // Add to batch persistence queue
                    batchPersistenceService.addPending(tempKey, candidateJson);

                    // ACK
                    redis.opsForStream().acknowledge(
                            queueService.getStreamKey(), queueService.getConsumerGroup(),
                            msg.getId()
                    );
                    log.info("Parse task consumed and ACKed: {}", msg.getId());

                } catch (Exception e) {
                    log.error("Failed to consume parse task: {}", msg.getId(), e);
                    // Still ACK to avoid infinite loop
                    try {
                        redis.opsForStream().acknowledge(
                                queueService.getStreamKey(), queueService.getConsumerGroup(),
                                msg.getId()
                        );
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("Stream consume skipped (Redis may be unavailable): {}", e.getMessage());
        }
    }
}
