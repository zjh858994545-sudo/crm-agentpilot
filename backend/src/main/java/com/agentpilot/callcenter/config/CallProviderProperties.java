package com.agentpilot.callcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "agentpilot.callcenter.provider")
public class CallProviderProperties {
    private String provider = "manual";
    private boolean enabled = false;
    private String endpoint = "";
    private String asrProvider = "manual";
    private String asrModel = "";
    private boolean asrEnabled = false;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAsrProvider() {
        return asrProvider;
    }

    public void setAsrProvider(String asrProvider) {
        this.asrProvider = asrProvider;
    }

    public String getAsrModel() {
        return asrModel;
    }

    public void setAsrModel(String asrModel) {
        this.asrModel = asrModel;
    }

    public boolean isAsrEnabled() {
        return asrEnabled;
    }

    public void setAsrEnabled(boolean asrEnabled) {
        this.asrEnabled = asrEnabled;
    }

    public boolean endpointConfigured() {
        return StringUtils.hasText(endpoint);
    }
}
