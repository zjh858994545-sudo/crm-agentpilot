package com.agentpilot.operations.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RetentionCleanupResult(
        boolean dryRun,
        LocalDateTime executedAt,
        long totalEligibleRows,
        long totalDeletedRows,
        List<RetentionCategoryResult> categories
) {
}
