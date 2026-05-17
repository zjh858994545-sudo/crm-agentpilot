package com.agentpilot.operations.vo;

public record UsageMetric(
        String key,
        String name,
        long today,
        long sevenDays,
        String unit,
        String note
) {
}
