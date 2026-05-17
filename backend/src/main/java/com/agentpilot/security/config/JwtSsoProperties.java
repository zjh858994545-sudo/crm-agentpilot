package com.agentpilot.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.security.jwt")
public class JwtSsoProperties {
    private boolean enabled = false;
    private String issuerUri = "";
    private String audience = "crm-agentpilot";
    private String tenantClaim = "tenant_id";
    private String salesRepClaim = "sales_rep_id";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public String getSalesRepClaim() {
        return salesRepClaim;
    }

    public void setSalesRepClaim(String salesRepClaim) {
        this.salesRepClaim = salesRepClaim;
    }
}
