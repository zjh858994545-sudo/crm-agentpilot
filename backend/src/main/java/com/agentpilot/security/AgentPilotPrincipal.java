package com.agentpilot.security;

import java.util.List;

public record AgentPilotPrincipal(
        Long userId,
        String tenantId,
        Long salesRepId,
        List<String> permissions
) {
}
