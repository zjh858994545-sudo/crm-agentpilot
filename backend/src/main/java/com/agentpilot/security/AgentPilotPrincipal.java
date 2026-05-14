package com.agentpilot.security;

import java.util.List;

public record AgentPilotPrincipal(
        Long userId,
        Long salesRepId,
        List<String> permissions
) {
}
