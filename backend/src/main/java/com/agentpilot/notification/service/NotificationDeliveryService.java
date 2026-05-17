package com.agentpilot.notification.service;

import com.agentpilot.notification.config.NotificationProperties;
import com.agentpilot.notification.entity.AgentPilotNotification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationProperties properties;
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public NotificationDeliveryService(NotificationProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.restClient = RestClient.create();
    }

    public void deliver(AgentPilotNotification notification) {
        if (!properties.webhookConfigured()) {
            log.info(
                    "notification delivery mode=log-only type={} recipient={} title={}",
                    notification.getType(),
                    notification.getRecipientUserId(),
                    notification.getTitle()
            );
            incrementDelivery("log_only");
            return;
        }
        try {
            restClient.post()
                    .uri(properties.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(notification))
                    .retrieve()
                    .toBodilessEntity();
            incrementDelivery("success");
        } catch (RuntimeException ex) {
            incrementDelivery("failed");
            log.warn(
                    "notification webhook delivery failed notificationId={} type={}",
                    notification.getId(),
                    notification.getType(),
                    ex
            );
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "mode", properties.webhookConfigured() ? "webhook" : "log-only",
                "webhookEnabled", properties.isWebhookEnabled(),
                "webhookConfigured", properties.webhookConfigured(),
                "webhookTimeoutSeconds", properties.getWebhookTimeoutSeconds()
        );
    }

    private Map<String, Object> payload(AgentPilotNotification notification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", notification.getTenantId());
        payload.put("recipientUserId", notification.getRecipientUserId());
        payload.put("salesRepId", notification.getSalesRepId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("content", notification.getContent());
        payload.put("actionUrl", notification.getActionUrl());
        payload.put("sourceType", notification.getSourceType());
        payload.put("sourceId", notification.getSourceId());
        payload.put("createdAt", notification.getCreatedAt());
        return payload;
    }

    private void incrementDelivery(String result) {
        Counter.builder("agentpilot_notification_webhook_delivery_total")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
