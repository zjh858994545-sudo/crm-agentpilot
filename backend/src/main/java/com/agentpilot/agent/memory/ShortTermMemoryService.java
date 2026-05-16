package com.agentpilot.agent.memory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShortTermMemoryService {
    private static final Duration TTL = Duration.ofHours(2);
    private static final int WINDOW_SIZE = 12;

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<Long, FallbackMemory> fallbackMemory = new ConcurrentHashMap<>();

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
        FallbackMemory memory = fallbackMemory.computeIfAbsent(sessionId, ignored -> new FallbackMemory());
        memory.messages().add(message);
        memory.refreshTtl();
        List<String> messages = memory.messages();
        if (messages.size() > WINDOW_SIZE) {
            memory.replaceMessages(new ArrayList<>(messages.subList(messages.size() - WINDOW_SIZE, messages.size())));
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
        FallbackMemory memory = fallbackMemory.get(sessionId);
        if (memory == null) {
            return List.of();
        }
        if (memory.expired()) {
            fallbackMemory.remove(sessionId);
            return List.of();
        }
        return List.copyOf(memory.messages());
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

    @Scheduled(fixedDelayString = "${agentpilot.memory.fallback-cleanup-delay-ms:600000}")
    public void cleanupExpiredFallbackEntries() {
        fallbackMemory.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private static final class FallbackMemory {
        private List<String> messages = new ArrayList<>();
        private Instant expiresAt = Instant.now().plus(TTL);

        List<String> messages() {
            return messages;
        }

        void replaceMessages(List<String> messages) {
            this.messages = messages;
        }

        void refreshTtl() {
            this.expiresAt = Instant.now().plus(TTL);
        }

        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
