package com.agentpilot.security;

import com.agentpilot.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@PreAuthorize("isAuthenticated()")
public class AuthController {
    private final RbacPrincipalService rbacPrincipalService;

    public AuthController(RbacPrincipalService rbacPrincipalService) {
        this.rbacPrincipalService = rbacPrincipalService;
    }

    @GetMapping("/me")
    public ApiResponse<AuthProfileView> me() {
        AgentPilotPrincipal principal = CurrentUser.require();
        return ApiResponse.ok(rbacPrincipalService.findProfileByUserId(principal.userId())
                .map(profile -> new AuthProfileView(
                        profile.userId(),
                        profile.tenantId(),
                        profile.username(),
                        profile.displayName(),
                        profile.salesRepId(),
                        profile.roles(),
                        profile.permissions(),
                        primaryRole(profile.roles())
                ))
                .orElseGet(() -> new AuthProfileView(
                        principal.userId(),
                        principal.tenantId(),
                        "local-principal",
                        "本地配置用户",
                        principal.salesRepId(),
                        List.of("local"),
                        principal.permissions(),
                        "admin"
                )));
    }

    private String primaryRole(List<String> roles) {
        if (roles.contains("system_admin")) {
            return "admin";
        }
        if (roles.contains("sales_manager")) {
            return "manager";
        }
        return "sales";
    }

    public record AuthProfileView(
            Long userId,
            String tenantId,
            String username,
            String displayName,
            Long salesRepId,
            List<String> roles,
            List<String> permissions,
            String primaryRole
    ) {
    }
}
