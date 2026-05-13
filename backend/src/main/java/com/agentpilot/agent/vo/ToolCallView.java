package com.agentpilot.agent.vo;

public record ToolCallView(
        Long id,
        String toolName,
        String toolType,
        String status,
        boolean requiresConfirmation,
        Long confirmationId
) {
}

