package com.agentpilot.agent.tool;

import java.util.List;
import java.util.Map;

public record AgentToolDefinition(
        String name,
        String description,
        ToolType type,
        boolean requiresConfirmation,
        List<String> requiredPermissions,
        Map<String, Object> parametersSchema
) {
}
