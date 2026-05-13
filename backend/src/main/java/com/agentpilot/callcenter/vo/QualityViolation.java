package com.agentpilot.callcenter.vo;

public record QualityViolation(
        String rule,
        String severity,
        String evidence,
        String suggestion
) {
}

