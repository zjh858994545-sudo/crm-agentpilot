package com.agentpilot.security;

import java.util.List;

public record AgentPilotPrincipal(
        Long userId,
        String tenantId,
        Long salesRepId,
        List<String> roles,
        List<String> permissions
) {
    public AgentPilotPrincipal(Long userId, String tenantId, Long salesRepId, List<String> permissions) {
        this(userId, tenantId, salesRepId, List.of(), permissions);
    }
}
