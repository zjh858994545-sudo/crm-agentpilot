package com.agentpilot.notification.service;

import com.agentpilot.notification.config.NotificationProperties;
import com.agentpilot.notification.entity.AgentPilotNotification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final Set<String> SUPPORTED_CHANNELS = Set.of("generic", "wecom", "dingtalk");

    private final NotificationProperties properties;
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public NotificationDeliveryService(NotificationProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getWebhookTimeoutSeconds()));
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public void deliver(AgentPilotNotification notification) {
        String channel = deliveryChannel();
        if (!properties.webhookConfigured()) {
            log.info(
                    "notification delivery mode=log-only channel={} type={} recipient={} title={}",
                    channel,
                    notification.getType(),
                    notification.getRecipientUserId(),
                    notification.getTitle()
            );
            incrementDelivery("log_only", channel);
            return;
        }
        try {
            restClient.post()
                    .uri(properties.getWebhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(notification, channel))
                    .retrieve()
                    .toBodilessEntity();
            incrementDelivery("success", channel);
        } catch (RuntimeException ex) {
            incrementDelivery("failed", channel);
            log.warn(
                    "notification webhook delivery failed notificationId={} channel={} type={}",
                    notification.getId(),
                    channel,
                    notification.getType(),
                    ex
            );
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "mode", properties.webhookConfigured() ? "webhook" : "log-only",
                "deliveryChannel", deliveryChannel(),
                "webhookEnabled", properties.isWebhookEnabled(),
                "webhookConfigured", properties.webhookConfigured(),
                "appBaseUrlConfigured", properties.appBaseUrlConfigured(),
                "webhookTimeoutSeconds", properties.getWebhookTimeoutSeconds()
        );
    }

    private Map<String, Object> payload(AgentPilotNotification notification, String channel) {
        return switch (channel) {
            case "wecom" -> wecomPayload(notification);
            case "dingtalk" -> dingtalkPayload(notification);
            default -> genericPayload(notification);
        };
    }

    private Map<String, Object> genericPayload(AgentPilotNotification notification) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", notification.getTenantId());
        payload.put("recipientUserId", notification.getRecipientUserId());
        payload.put("salesRepId", notification.getSalesRepId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("content", notification.getContent());
        payload.put("actionUrl", absoluteActionUrl(notification.getActionUrl()));
        payload.put("sourceType", notification.getSourceType());
        payload.put("sourceId", notification.getSourceId());
        payload.put("createdAt", notification.getCreatedAt());
        return payload;
    }

    private Map<String, Object> wecomPayload(AgentPilotNotification notification) {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("content", markdownText(notification));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    private Map<String, Object> dingtalkPayload(AgentPilotNotification notification) {
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", safeText(notification.getTitle(), "CRM-AgentPilot 提醒"));
        markdown.put("text", markdownText(notification));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);
        return payload;
    }

    private String markdownText(AgentPilotNotification notification) {
        String title = safeText(notification.getTitle(), "CRM-AgentPilot 提醒");
        String content = safeText(notification.getContent(), "你有一条待处理提醒。");
        String actionUrl = absoluteActionUrl(notification.getActionUrl());
        StringBuilder builder = new StringBuilder()
                .append("### ").append(escapeMarkdown(title)).append("\n\n")
                .append(escapeMarkdown(content)).append("\n\n")
                .append("> 类型：").append(escapeMarkdown(safeText(notification.getType(), "-"))).append("\n")
                .append("> 来源：").append(escapeMarkdown(safeText(notification.getSourceType(), "-")))
                .append("#").append(escapeMarkdown(safeText(notification.getSourceId(), "-"))).append("\n");
        if (actionUrl != null && !actionUrl.isBlank()) {
            builder.append("\n[打开处理](").append(actionUrl).append(")");
        }
        return builder.toString();
    }

    private String deliveryChannel() {
        String channel = properties.normalizedDeliveryChannel();
        if (SUPPORTED_CHANNELS.contains(channel)) {
            return channel;
        }
        log.warn("unsupported notification delivery channel={}, fallback to generic", channel);
        return "generic";
    }

    private String absoluteActionUrl(String actionUrl) {
        if (actionUrl == null || actionUrl.isBlank()) {
            return "";
        }
        if (actionUrl.startsWith("http://") || actionUrl.startsWith("https://")) {
            return actionUrl;
        }
        if (!properties.appBaseUrlConfigured()) {
            return actionUrl;
        }
        String baseUrl = properties.getAppBaseUrl().replaceAll("/+$", "");
        String path = actionUrl.startsWith("/") ? actionUrl : "/" + actionUrl;
        return baseUrl + path;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String escapeMarkdown(String value) {
        return safeText(value, "").replace("\\", "\\\\").replace("`", "\\`");
    }

    private void incrementDelivery(String result, String channel) {
        Counter.builder("agentpilot_notification_webhook_delivery_total")
                .tag("result", result)
                .tag("channel", channel)
                .register(meterRegistry)
                .increment();
    }
}
