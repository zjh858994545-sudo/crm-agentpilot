package com.agentpilot.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agentpilot.security")
public class AgentPilotSecurityProperties {
    private String mode = "permissive";
    private String apiToken = "";
    private Long demoUserId = 1L;
    private Long demoSalesRepId = 1L;
    private List<String> permissions = new ArrayList<>(List.of(
            "agent:use",
            "crm:read",
            "crm:write",
            "knowledge:read",
            "knowledge:write",
            "product:read",
            "evaluation:run",
            "events:read",
            "events:write"
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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
