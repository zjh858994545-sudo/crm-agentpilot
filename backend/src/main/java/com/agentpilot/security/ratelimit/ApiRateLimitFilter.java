package com.agentpilot.security.ratelimit;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.AgentPilotPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final long ONE_MINUTE_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long STALE_BUCKET_NANOS = Duration.ofMinutes(30).toNanos();
    private static final long REDIS_RETRY_NANOS = Duration.ofSeconds(30).toNanos();

    private final ApiRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final MeterRegistry meterRegistry;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastRedisFailureNanos = 0L;

    public ApiRateLimitFilter(
            ApiRateLimitProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisTemplateProvider = redisTemplateProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled() || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitRule rule = ruleFor(request.getRequestURI());
        String key = rule.name() + ":" + callerKey(request);
        if (!tryConsume(rule, key)) {
            recordRateLimited(rule.name(), request.getRequestURI());
            writeRateLimited(response, rule.name());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(LimitRule rule, String key) {
        Optional<Boolean> redisDecision = tryConsumeRedis(rule, key);
        if (redisDecision.isPresent()) {
            return redisDecision.get();
        }
        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> new TokenBucket(rule.capacity(), rule.refillPerMinute()));
        return bucket.tryConsume();
    }

    private Optional<Boolean> tryConsumeRedis(LimitRule rule, String key) {
        if ("memory".equalsIgnoreCase(properties.getBackend())) {
            return Optional.empty();
        }
        long nowNanos = System.nanoTime();
        if (!"redis".equalsIgnoreCase(properties.getBackend()) && nowNanos - lastRedisFailureNanos < REDIS_RETRY_NANOS) {
            return Optional.empty();
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return Optional.empty();
            }
            long currentMinute = Instant.now().getEpochSecond() / 60;
            String redisKey = "agentpilot:rate-limit:" + key + ":" + currentMinute;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, 70, TimeUnit.SECONDS);
            }
            return Optional.of(count == null || count <= rule.capacity());
        } catch (RuntimeException ex) {
            lastRedisFailureNanos = nowNanos;
            if ("redis".equalsIgnoreCase(properties.getBackend())) {
                throw ex;
            }
            return Optional.empty();
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/")
                || "/api/health".equals(uri)
                || uri.startsWith("/api/security/status");
    }

    private LimitRule ruleFor(String uri) {
        if (uri.startsWith("/api/agent/chat")) {
            return new LimitRule("agent-chat", properties.getAgentCapacity(), properties.getAgentRefillPerMinute());
        }
        if (uri.startsWith("/api/model/chat")) {
            return new LimitRule("model-chat", properties.getModelCapacity(), properties.getModelRefillPerMinute());
        }
        return new LimitRule("default", properties.getDefaultCapacity(), properties.getDefaultRefillPerMinute());
    }

    private String callerKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AgentPilotPrincipal principal) {
            return "user:" + principal.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletResponse response, String bucketName) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.fail("RATE_LIMITED", "Too many requests for bucket: " + bucketName)
        );
    }

    private void recordRateLimited(String bucketName, String uri) {
        Counter.builder("agentpilot_rate_limited_total")
                .tag("bucket", bucketName)
                .tag("uri", uri == null ? "unknown" : uri)
                .register(meterRegistry)
                .increment();
    }

    @Scheduled(fixedDelayString = "${agentpilot.rate-limit.cleanup-delay-ms:600000}")
    public void cleanupStaleBuckets() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeenNanos() > STALE_BUCKET_NANOS);
    }

    private record LimitRule(String name, int capacity, int refillPerMinute) {
    }

    private static final class TokenBucket {
        private final int capacity;
        private final int refillPerMinute;
        private double tokens;
        private long lastRefillNanos;
        private long lastSeenNanos;

        private TokenBucket(int capacity, int refillPerMinute) {
            this.capacity = Math.max(1, capacity);
            this.refillPerMinute = Math.max(1, refillPerMinute);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastSeenNanos = this.lastRefillNanos;
        }

        synchronized boolean tryConsume() {
            refill();
            lastSeenNanos = System.nanoTime();
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        synchronized long lastSeenNanos() {
            return lastSeenNanos;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double refillTokens = (elapsed / (double) ONE_MINUTE_NANOS) * refillPerMinute;
            tokens = Math.min(capacity, tokens + refillTokens);
            lastRefillNanos = now;
        }
    }
}
