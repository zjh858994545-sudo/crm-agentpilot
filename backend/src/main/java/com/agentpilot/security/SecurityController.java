package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.RbacPrincipalService;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import com.agentpilot.security.ratelimit.ApiRateLimitProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@PreAuthorize("hasAuthority('agent:use')")
public class SecurityController {
    private final AgentPilotSecurityProperties properties;
    private final RbacPrincipalService rbacPrincipalService;
    private final ApiRateLimitProperties rateLimitProperties;

    public SecurityController(
            AgentPilotSecurityProperties properties,
            RbacPrincipalService rbacPrincipalService,
            ApiRateLimitProperties rateLimitProperties
    ) {
        this.properties = properties;
        this.rbacPrincipalService = rbacPrincipalService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        boolean tokenConfigured = StringUtils.hasText(properties.getApiToken());
        long rbacUserCount = rbacPrincipalService.activeUserCount();
        long rbacRoleCount = rbacPrincipalService.roleCount();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", properties.getMode());
        body.put("strict", properties.strict());
        body.put("demoUserId", properties.getDemoUserId());
        body.put("demoSalesRepId", properties.getDemoSalesRepId());
        body.put("permissionCount", properties.getPermissions().size());
        body.put("rbacEnabled", rbacUserCount > 0);
        body.put("rbacUserCount", rbacUserCount);
        body.put("rbacRoleCount", rbacRoleCount);
        body.put("tokenConfigured", tokenConfigured);
        body.put("strictWithoutToken", properties.strict() && !tokenConfigured && rbacUserCount == 0);
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
    @PreAuthorize("hasAuthority('events:read')")
    public ApiResponse<List<RbacPrincipalService.UserProfile>> users() {
        return ApiResponse.ok(rbacPrincipalService.listProfiles());
    }
}
