package com.agentpilot.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.notifications")
public class NotificationProperties {
    private boolean webhookEnabled;
    private String webhookUrl = "";
    private int webhookTimeoutSeconds = 5;

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
}
