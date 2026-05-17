package com.agentpilot.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agentpilot.security")
public class AgentPilotSecurityProperties {
    private String mode = "permissive";
    private String apiToken = "";
    private String demoTenantId = "demo";
    private Long demoUserId = 1L;
    private Long demoSalesRepId = 1L;
    private boolean seedUsersEnabled = true;
    private long tokenAuditMinIntervalSeconds = 60L;
    private List<String> permissions = new ArrayList<>(List.of(
            "agent:use",
            "crm:read",
            "crm:write",
            "knowledge:read",
            "knowledge:write",
            "product:read",
            "evaluation:run",
            "events:read",
            "events:write",
            "ops:read",
            "ops:write"
    ));

    public boolean strict() {
        return "strict".equalsIgnoreCase(mode);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getDemoTenantId() {
        return demoTenantId;
    }

    public void setDemoTenantId(String demoTenantId) {
        this.demoTenantId = demoTenantId;
    }

    public Long getDemoUserId() {
        return demoUserId;
    }

    public void setDemoUserId(Long demoUserId) {
        this.demoUserId = demoUserId;
    }

    public Long getDemoSalesRepId() {
        return demoSalesRepId;
    }

    public void setDemoSalesRepId(Long demoSalesRepId) {
        this.demoSalesRepId = demoSalesRepId;
    }

    public boolean isSeedUsersEnabled() {
        return seedUsersEnabled;
    }

    public void setSeedUsersEnabled(boolean seedUsersEnabled) {
        this.seedUsersEnabled = seedUsersEnabled;
    }

    public long getTokenAuditMinIntervalSeconds() {
        return tokenAuditMinIntervalSeconds;
    }

    public void setTokenAuditMinIntervalSeconds(long tokenAuditMinIntervalSeconds) {
        this.tokenAuditMinIntervalSeconds = tokenAuditMinIntervalSeconds;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
