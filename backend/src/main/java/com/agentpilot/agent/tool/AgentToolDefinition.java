package com.agentpilot.agent.tool;

import java.util.List;

public record AgentToolDefinition(
        String name,
        String description,
        ToolType type,
        boolean requiresConfirmation,
        List<String> requiredPermissions
) {
}

