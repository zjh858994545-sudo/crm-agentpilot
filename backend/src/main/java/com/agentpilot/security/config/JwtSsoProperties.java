package com.agentpilot.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.security.jwt")
public class JwtSsoProperties {
    private boolean enabled = false;
    private String issuerUri = "";
    private String audience = "crm-agentpilot";
    private String userIdClaim = "user_id";
    private String tenantClaim = "tenant_id";
    private String salesRepClaim = "sales_rep_id";
    private String rolesClaim = "roles";
    private String permissionsClaim = "permissions";

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

    public String getUserIdClaim() {
        return userIdClaim;
    }

    public void setUserIdClaim(String userIdClaim) {
        this.userIdClaim = userIdClaim;
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

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public String getPermissionsClaim() {
        return permissionsClaim;
    }

    public void setPermissionsClaim(String permissionsClaim) {
        this.permissionsClaim = permissionsClaim;
    }
}
