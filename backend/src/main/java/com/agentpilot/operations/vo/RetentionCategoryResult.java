package com.agentpilot.operations.vo;

import java.time.LocalDateTime;

public record RetentionCategoryResult(
        String key,
        String name,
        int retentionDays,
        LocalDateTime cutoffAt,
        long eligibleRows,
        long deletedRows,
        long protectedRows
) {
}
