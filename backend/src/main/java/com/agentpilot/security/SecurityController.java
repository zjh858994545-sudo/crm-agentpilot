package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.operations.service.AdminAuditService;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import com.agentpilot.security.config.JwtSsoProperties;
import com.agentpilot.security.ratelimit.ApiRateLimitProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/security")
@PreAuthorize("hasAuthority('agent:use')")
public class SecurityController {
    private final AgentPilotSecurityProperties properties;
    private final JwtSsoProperties jwtSsoProperties;
    private final RbacPrincipalService rbacPrincipalService;
    private final ApiRateLimitProperties rateLimitProperties;
    private final AdminAuditService adminAuditService;

    public SecurityController(
            AgentPilotSecurityProperties properties,
            JwtSsoProperties jwtSsoProperties,
            RbacPrincipalService rbacPrincipalService,
            ApiRateLimitProperties rateLimitProperties,
            AdminAuditService adminAuditService
    ) {
        this.properties = properties;
        this.jwtSsoProperties = jwtSsoProperties;
        this.rbacPrincipalService = rbacPrincipalService;
        this.rateLimitProperties = rateLimitProperties;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        boolean tokenConfigured = StringUtils.hasText(properties.getApiToken());
        long rbacUserCount = rbacPrincipalService.activeUserCount();
        long rbacRoleCount = rbacPrincipalService.roleCount();
        long activeTenantCount = rbacPrincipalService.activeTenantCount();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", properties.getMode());
        body.put("strict", properties.strict());
        body.put("demoTenantId", properties.getDemoTenantId());
        body.put("demoUserId", properties.getDemoUserId());
        body.put("demoSalesRepId", properties.getDemoSalesRepId());
        body.put("permissionCount", properties.getPermissions().size());
        body.put("rbacEnabled", rbacUserCount > 0);
        body.put("rbacUserCount", rbacUserCount);
        body.put("rbacRoleCount", rbacRoleCount);
        body.put("activeTenantCount", activeTenantCount);
        body.put("tokenConfigured", tokenConfigured);
        body.put("seedUsersEnabled", properties.isSeedUsersEnabled());
        body.put("strictWithoutToken", properties.strict() && !tokenConfigured && rbacUserCount == 0);
        body.put("jwt", Map.of(
                "enabled", jwtSsoProperties.isEnabled(),
                "issuerConfigured", StringUtils.hasText(jwtSsoProperties.getIssuerUri()),
                "audience", jwtSsoProperties.getAudience(),
                "userIdClaim", jwtSsoProperties.getUserIdClaim(),
                "tenantClaim", jwtSsoProperties.getTenantClaim(),
                "salesRepClaim", jwtSsoProperties.getSalesRepClaim(),
                "rolesClaim", jwtSsoProperties.getRolesClaim(),
                "permissionsClaim", jwtSsoProperties.getPermissionsClaim(),
                "tenantAllowListEnabled", !jwtSsoProperties.normalizedAllowedTenants().isEmpty(),
                "allowedTenantCount", jwtSsoProperties.normalizedAllowedTenants().size()
        ));
        body.put("rateLimit", Map.of(
                "enabled", rateLimitProperties.isEnabled(),
                "backend", rateLimitProperties.getBackend(),
                "defaultCapacity", rateLimitProperties.getDefaultCapacity(),
                "defaultRefillPerMinute", rateLimitProperties.getDefaultRefillPerMinute(),
                "agentCapacity", rateLimitProperties.getAgentCapacity(),
                "agentRefillPerMinute", rateLimitProperties.getAgentRefillPerMinute(),
                "modelCapacity", rateLimitProperties.getModelCapacity(),
                "modelRefillPerMinute", rateLimitProperties.getModelRefillPerMinute()
        ));
        return ApiResponse.ok(body);
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ops:read')")
    public ApiResponse<List<RbacPrincipalService.UserProfile>> users() {
        return ApiResponse.ok(rbacPrincipalService.listProfiles(CurrentUser.tenantId()));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<UserProvisioningResponse> createUser(@RequestBody UserUpsertRequest request) {
        String tenantId = resolveTenantId(request == null ? null : request.tenantId());
        RbacPrincipalService.UserProvisioningResult result = rbacPrincipalService.createUser(
                tenantId,
                request == null ? null : request.username(),
                request == null ? null : request.displayName(),
                request == null ? null : request.salesRepId(),
                request == null ? null : request.roles()
        );
        adminAuditService.record(
                "security.user.create",
                "agentpilot_user",
                String.valueOf(result.profile().userId()),
                "Created RBAC user " + result.profile().username()
        );
        return ApiResponse.ok(UserProvisioningResponse.from(result));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<RbacPrincipalService.UserProfile> updateUser(
            @PathVariable Long userId,
            @RequestBody UserUpsertRequest request
    ) {
        String tenantId = resolveTenantId(request == null ? null : request.tenantId());
        RbacPrincipalService.UserProfile profile = rbacPrincipalService.updateUser(
                userId,
                tenantId,
                request == null ? null : request.displayName(),
                request == null ? null : request.salesRepId(),
                request == null ? null : request.roles()
        );
        adminAuditService.record(
                "security.user.update",
                "agentpilot_user",
                String.valueOf(profile.userId()),
                "Updated RBAC user " + profile.username()
        );
        return ApiResponse.ok(profile);
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<RbacPrincipalService.UserProfile> changeUserStatus(
            @PathVariable Long userId,
            @RequestBody UserStatusRequest request
    ) {
        RbacPrincipalService.UserProfile profile = rbacPrincipalService.changeUserStatus(
                userId,
                CurrentUser.tenantId(),
                request == null ? null : request.status()
        );
        adminAuditService.record(
                "security.user.status",
                "agentpilot_user",
                String.valueOf(profile.userId()),
                "Changed RBAC user status to " + profile.status()
        );
        return ApiResponse.ok(profile);
    }

    @PostMapping("/users/{userId}/token")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<UserProvisioningResponse> regenerateUserToken(@PathVariable Long userId) {
        RbacPrincipalService.UserProvisioningResult result = rbacPrincipalService.regenerateToken(userId, CurrentUser.tenantId());
        adminAuditService.record(
                "security.user.token.rotate",
                "agentpilot_user",
                String.valueOf(result.profile().userId()),
                "Rotated RBAC token for user " + result.profile().username()
        );
        return ApiResponse.ok(UserProvisioningResponse.from(result));
    }

    private String resolveTenantId(String requestedTenantId) {
        AgentPilotPrincipal current = CurrentUser.require();
        if (current.roles().contains("system_admin") && StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId.trim();
        }
        return Objects.requireNonNull(current.tenantId(), "tenantId is required");
    }

    public record UserUpsertRequest(
            String tenantId,
            String username,
            String displayName,
            Long salesRepId,
            List<String> roles
    ) {
    }

    public record UserStatusRequest(String status) {
    }

    public record UserProvisioningResponse(
            RbacPrincipalService.UserProfile profile,
            String apiToken
    ) {
        static UserProvisioningResponse from(RbacPrincipalService.UserProvisioningResult result) {
            return new UserProvisioningResponse(result.profile(), result.apiToken());
        }
    }
}
