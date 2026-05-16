package com.agentpilot.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.rate-limit")
public class ApiRateLimitProperties {
    private boolean enabled = true;
    private String backend = "auto";
    private int defaultCapacity = 300;
    private int defaultRefillPerMinute = 300;
    private int agentCapacity = 60;
    private int agentRefillPerMinute = 60;
    private int modelCapacity = 30;
    private int modelRefillPerMinute = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public int getDefaultRefillPerMinute() {
        return defaultRefillPerMinute;
    }

    public void setDefaultRefillPerMinute(int defaultRefillPerMinute) {
        this.defaultRefillPerMinute = defaultRefillPerMinute;
    }

    public int getAgentCapacity() {
        return agentCapacity;
    }

    public void setAgentCapacity(int agentCapacity) {
        this.agentCapacity = agentCapacity;
    }

    public int getAgentRefillPerMinute() {
        return agentRefillPerMinute;
    }

    public void setAgentRefillPerMinute(int agentRefillPerMinute) {
        this.agentRefillPerMinute = agentRefillPerMinute;
    }

    public int getModelCapacity() {
        return modelCapacity;
    }

    public void setModelCapacity(int modelCapacity) {
        this.modelCapacity = modelCapacity;
    }

    public int getModelRefillPerMinute() {
        return modelRefillPerMinute;
    }

    public void setModelRefillPerMinute(int modelRefillPerMinute) {
        this.modelRefillPerMinute = modelRefillPerMinute;
    }
}
