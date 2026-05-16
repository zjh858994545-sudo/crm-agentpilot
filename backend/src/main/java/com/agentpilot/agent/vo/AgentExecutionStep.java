package com.agentpilot.agent.vo;

import java.time.LocalDateTime;

public record AgentExecutionStep(
        String key,
        String title,
        String description,
        String status,
        String category,
        String toolName,
        String toolType,
        Long latencyMs,
        Long confirmationId,
        String confirmationStatus,
        LocalDateTime occurredAt,
        String inputJson,
        String outputJson
) {
}
