package com.agentpilot.operations.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RetentionStatus(
        boolean enabled,
        boolean scheduledCleanupEnabled,
        String cleanupCron,
        int maxDeleteRowsPerRun,
        LocalDateTime checkedAt,
        long totalEligibleRows,
        List<RetentionCategoryStatus> categories
) {
}
