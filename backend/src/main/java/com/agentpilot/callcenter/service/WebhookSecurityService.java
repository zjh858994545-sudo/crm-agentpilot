package com.agentpilot.callcenter.service;

import com.agentpilot.callcenter.config.CallCenterWebhookProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class WebhookSecurityService {
    public static final String CALL_ENDED_ENDPOINT = "call-ended-events";

    private final CallCenterWebhookProperties properties;
    private final WebhookReplayGuardService replayGuardService;
    private final MeterRegistry meterRegistry;

    public WebhookSecurityService(
            CallCenterWebhookProperties properties,
            WebhookReplayGuardService replayGuardService,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.replayGuardService = replayGuardService;
        this.meterRegistry = meterRegistry;
    }

    public void verifyCallEndedEvent(String tenantId, String rawBody, String timestampHeader, String nonce, String signature) {
        if (!properties.isSignatureEnabled()) {
            return;
        }
        if (!properties.secretConfigured()) {
            reject("secret_missing");
        }
        long timestamp = parseTimestamp(timestampHeader);
        long now = Instant.now().getEpochSecond();
        long maxSkewSeconds = Math.max(60, properties.getMaxSkewSeconds());
        if (Math.abs(now - timestamp) > maxSkewSeconds) {
            reject("timestamp_skew");
        }
        if (nonce == null || nonce.isBlank()) {
            reject("nonce_missing");
        }
        if (signature == null || signature.isBlank()) {
            reject("signature_missing");
        }

        String expected = hmacHex(timestamp + "." + nonce + "." + rawBody, properties.getSecret());
        String normalizedSignature = normalizeSignature(signature);
        if (!constantTimeEquals(expected, normalizedSignature)) {
            reject("signature_invalid");
        }

        boolean remembered = replayGuardService.remember(
                tenantId,
                CALL_ENDED_ENDPOINT,
                nonce,
                sha256Hex(normalizedSignature),
                maxSkewSeconds
        );
        if (!remembered) {
            reject("nonce_replayed");
        }
        Counter.builder("agentpilot_webhook_accepted_total")
                .tag("endpoint", CALL_ENDED_ENDPOINT)
                .register(meterRegistry)
                .increment();
    }

    public boolean enabled() {
        return properties.isSignatureEnabled();
    }

    public long maxSkewSeconds() {
        return properties.getMaxSkewSeconds();
    }

    public boolean secretConfigured() {
        return properties.secretConfigured();
    }

    private long parseTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            reject("timestamp_missing");
        }
        try {
            long parsed = Long.parseLong(timestampHeader);
            if (parsed > 10_000_000_000L) {
                return parsed / 1000;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            reject("timestamp_invalid");
            return 0;
        }
    }

    private String normalizeSignature(String signature) {
        String normalized = signature.trim().toLowerCase();
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring("sha256=".length());
        }
        if (!normalized.matches("[0-9a-f]{64}")) {
            reject("signature_format");
        }
        return normalized;
    }

    private String hmacHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook signature verification is unavailable", ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook replay hash is unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void reject(String reason) {
        Counter.builder("agentpilot_webhook_rejected_total")
                .tag("endpoint", CALL_ENDED_ENDPOINT)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
        throw new AccessDeniedException("webhook request rejected: " + reason);
    }
}
