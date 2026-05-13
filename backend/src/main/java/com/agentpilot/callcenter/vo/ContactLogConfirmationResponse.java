package com.agentpilot.callcenter.vo;

import java.util.Map;

public record ContactLogConfirmationResponse(
        Long runId,
        Long confirmationId,
        String actionSummary,
        Map<String, Object> payload
) {
}

