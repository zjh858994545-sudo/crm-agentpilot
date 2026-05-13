package com.agentpilot.callcenter.vo;

public record CallSummaryResponse(
        String summary,
        String customerIntent,
        String objections,
        String nextAction
) {
}

