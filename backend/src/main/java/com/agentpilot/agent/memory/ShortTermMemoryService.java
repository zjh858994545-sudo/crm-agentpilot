package com.agentpilot.agent.memory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortTermMemoryService {
    private static final Duration TTL = Duration.ofHours(2);
    private static final int WINDOW_SIZE = 12;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<Long, List<String>> fallbackMemory = new ConcurrentHashMap<>();

    public ShortTermMemoryService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public void append(Long sessionId, String role, String content) {
        String message = role + ": " + content;
        String key = key(sessionId);
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                redisTemplate.opsForList().rightPush(key, message);
                redisTemplate.opsForList().trim(key, -WINDOW_SIZE, -1);
                redisTemplate.expire(key, TTL);
                return;
            }
        } catch (RuntimeException ignored) {
            // Local tests run without Redis; fallback keeps the service deterministic.
        }
        fallbackMemory.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(message);
        List<String> messages = fallbackMemory.get(sessionId);
        if (messages.size() > WINDOW_SIZE) {
            fallbackMemory.put(sessionId, new ArrayList<>(messages.subList(messages.size() - WINDOW_SIZE, messages.size())));
        }
    }

    public List<String> recent(Long sessionId) {
        String key = key(sessionId);
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                List<String> values = redisTemplate.opsForList().range(key, -WINDOW_SIZE, -1);
                if (values != null) {
                    return values;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to local memory.
        }
        return List.copyOf(fallbackMemory.getOrDefault(sessionId, List.of()));
    }

    public String summarize(Long sessionId) {
        List<String> messages = recent(sessionId);
        if (messages.isEmpty()) {
            return "暂无短期记忆";
        }
        return String.join(" | ", messages);
    }

    private String key(Long sessionId) {
        return "agent:session:" + sessionId + ":messages";
    }
}

