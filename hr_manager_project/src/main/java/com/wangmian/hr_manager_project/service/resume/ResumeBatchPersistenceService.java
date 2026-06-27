package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.model.document.Candidate;
import com.wangmian.hr_manager_project.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ResumeBatchPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ResumeBatchPersistenceService.class);

    @Value("${hr.resume.batch.size:10}")
    private int batchSize;

    private final CandidateRepository candidateRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final List<PendingEntry> pendingEntries = new CopyOnWriteArrayList<>();

    public ResumeBatchPersistenceService(CandidateRepository candidateRepository, StringRedisTemplate redis) {
        this.candidateRepository = candidateRepository;
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    public void addPending(String tempKey, String candidateJson) {
        pendingEntries.add(new PendingEntry(tempKey, candidateJson));
        if (pendingEntries.size() >= batchSize) {
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${hr.resume.batch.interval-ms:5000}")
    public void scheduledFlush() {
        if (!pendingEntries.isEmpty()) {
            flush();
        }
    }

    public synchronized void flush() {
        if (pendingEntries.isEmpty()) return;

        List<PendingEntry> batch = new ArrayList<>(pendingEntries);
        pendingEntries.clear();

        List<Candidate> candidatesToSave = new ArrayList<>();

        for (PendingEntry entry : batch) {
            try {
                Candidate candidate = mapper.readValue(entry.candidateJson, Candidate.class);
                candidate.setCreatedAt(LocalDateTime.now());
                candidate.setUpdatedAt(LocalDateTime.now());
                candidatesToSave.add(candidate);
            } catch (Exception e) {
                log.error("Failed to deserialize candidate from temp key: {}", entry.tempKey, e);
            }
        }

        if (candidatesToSave.isEmpty()) return;

        try {
            List<Candidate> saved = candidateRepository.saveAll(candidatesToSave);
            log.info("Batch persisted {} candidates to MongoDB", saved.size());

            for (PendingEntry entry : batch) {
                try {
                    redis.delete(entry.tempKey);
                } catch (Exception e) {
                    log.debug("Failed to delete temp key: {}", entry.tempKey);
                }
            }
        } catch (Exception e) {
            log.error("Batch persist failed, returning {} entries to pending queue", batch.size(), e);
            pendingEntries.addAll(batch);
        }
    }

    private record PendingEntry(String tempKey, String candidateJson) {}
}
