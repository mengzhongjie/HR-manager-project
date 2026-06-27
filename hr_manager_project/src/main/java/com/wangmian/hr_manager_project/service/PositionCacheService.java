package com.wangmian.hr_manager_project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wangmian.hr_manager_project.model.document.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Service
public class PositionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PositionCacheService.class);
    private static final String CACHE_KEY = "cache:positions";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public PositionCacheService(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /** Cache-Aside: 读缓存 → 未命中则查数据库 → 写入缓存 → 返回 */
    public List<Position> getPositions(Supplier<List<Position>> dbFallback) {
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                List<Position> result = mapper.readValue(cached, new TypeReference<>() {});
                // 续期：命中时刷新 TTL
                redis.expire(CACHE_KEY, CACHE_TTL);
                log.debug("Cache hit: positions ({} items), TTL renewed", result.size());
                return result;
            }
        } catch (Exception e) {
            log.debug("Cache read failed, falling back to DB", e);
        }

        List<Position> positions = dbFallback.get();
        if (positions != null && !positions.isEmpty()) {
            try {
                String json = mapper.writeValueAsString(positions);
                redis.opsForValue().set(CACHE_KEY, json, CACHE_TTL);
                log.info("Cache miss: loaded {} positions into Redis", positions.size());
            } catch (Exception e) {
                log.warn("Failed to write position cache", e);
            }
        }
        return positions != null ? positions : Collections.emptyList();
    }

    /** 使缓存失效（写操作后调用） */
    public void evictPositionCache() {
        try {
            redis.delete(CACHE_KEY);
            log.debug("Position cache evicted");
        } catch (Exception e) {
            log.warn("Failed to evict position cache", e);
        }
    }
}
