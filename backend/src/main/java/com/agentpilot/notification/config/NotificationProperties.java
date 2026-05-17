package com.agentpilot.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.notifications")
public class NotificationProperties {
    private String deliveryChannel = "generic";
    private String appBaseUrl = "";
    private boolean webhookEnabled;
    private String webhookUrl = "";
    private int webhookTimeoutSeconds = 5;

    public String getDeliveryChannel() {
        return deliveryChannel;
    }

    public void setDeliveryChannel(String deliveryChannel) {
        this.deliveryChannel = deliveryChannel;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public int getWebhookTimeoutSeconds() {
        return webhookTimeoutSeconds;
    }

    public void setWebhookTimeoutSeconds(int webhookTimeoutSeconds) {
        this.webhookTimeoutSeconds = webhookTimeoutSeconds;
    }

    public boolean webhookConfigured() {
        return webhookEnabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    public String normalizedDeliveryChannel() {
        if (deliveryChannel == null || deliveryChannel.isBlank()) {
            return "generic";
        }
        return deliveryChannel.trim().toLowerCase();
    }

    public boolean appBaseUrlConfigured() {
        return appBaseUrl != null && !appBaseUrl.isBlank();
    }
}
