package com.wangmian.hr_manager_project.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ResumeParseQueueService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseQueueService.class);
    private static final String STREAM_KEY = "parsing_queue";
    private static final String CONSUMER_GROUP = "resume-parsers";
    private static final String CONSUMER_NAME = "parser-1";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ResumeParseQueueService(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        initStream();
    }

    private void initStream() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
        } catch (Exception e) {
            // Group may already exist
            log.debug("Stream group already exists or Redis unavailable: {}", e.getMessage());
        }
    }

    public void pushParseTask(String taskJson) {
        redis.opsForStream().add(STREAM_KEY, Map.of("payload", taskJson));
        log.info("Parse task pushed to stream");
    }

    public StringRedisTemplate getRedis() {
        return redis;
    }

    public String getStreamKey() { return STREAM_KEY; }
    public String getConsumerGroup() { return CONSUMER_GROUP; }
    public String getConsumerName() { return CONSUMER_NAME; }
}
