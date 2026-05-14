package com.agentpilot.agent.vo;

import jakarta.validation.constraints.NotNull;

public record ConfirmationDecisionRequest(
        @NotNull
        Long userId
) {
}
