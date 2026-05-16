package com.agentpilot.operations.vo;

import java.time.LocalDateTime;

public record RetentionCategoryStatus(
        String key,
        String name,
        int retentionDays,
        LocalDateTime cutoffAt,
        long eligibleRows,
        long protectedRows,
        String protectionRule
) {
}
