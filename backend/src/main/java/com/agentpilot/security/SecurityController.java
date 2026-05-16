package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.security.RbacPrincipalService;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/security")
@PreAuthorize("hasAuthority('agent:use')")
public class SecurityController {
    private final AgentPilotSecurityProperties properties;
    private final RbacPrincipalService rbacPrincipalService;

    public SecurityController(AgentPilotSecurityProperties properties, RbacPrincipalService rbacPrincipalService) {
        this.properties = properties;
        this.rbacPrincipalService = rbacPrincipalService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        boolean tokenConfigured = StringUtils.hasText(properties.getApiToken());
        long rbacUserCount = rbacPrincipalService.activeUserCount();
        long rbacRoleCount = rbacPrincipalService.roleCount();
        return ApiResponse.ok(Map.of(
                "mode", properties.getMode(),
                "strict", properties.strict(),
                "demoUserId", properties.getDemoUserId(),
                "demoSalesRepId", properties.getDemoSalesRepId(),
                "permissionCount", properties.getPermissions().size(),
                "rbacEnabled", rbacUserCount > 0,
                "rbacUserCount", rbacUserCount,
                "rbacRoleCount", rbacRoleCount,
                "tokenConfigured", tokenConfigured,
                "strictWithoutToken", properties.strict() && !tokenConfigured && rbacUserCount == 0
        ));
    }
}
