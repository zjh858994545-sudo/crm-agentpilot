package com.agentpilot.agent.vo;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        Long sessionId,
        Long userId,
        Long salesRepId,
        Long customerId,
        @NotBlank String message
) {
}

