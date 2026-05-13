package com.agentpilot.agent.vo;

import java.util.List;

public record AgentChatResponse(
        String type,
        Long sessionId,
        Long runId,
        String answer,
        Long confirmationId,
        String actionSummary,
        Object payload,
        List<ToolCallView> toolCalls
) {
}
