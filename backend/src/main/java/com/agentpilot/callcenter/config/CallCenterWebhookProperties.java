package com.agentpilot.callcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.callcenter.webhook")
public class CallCenterWebhookProperties {
    private boolean signatureEnabled;
    private String secret = "";
    private long maxSkewSeconds = 300;

    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getMaxSkewSeconds() {
        return maxSkewSeconds;
    }

    public void setMaxSkewSeconds(long maxSkewSeconds) {
        this.maxSkewSeconds = maxSkewSeconds;
    }

    public boolean secretConfigured() {
        return secret != null && !secret.isBlank();
    }
}
