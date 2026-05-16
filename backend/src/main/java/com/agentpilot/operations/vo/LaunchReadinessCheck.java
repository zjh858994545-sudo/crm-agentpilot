package com.agentpilot.operations.vo;

public record LaunchReadinessCheck(
        String key,
        String name,
        String status,
        String severity,
        String detail,
        String action
) {
}
