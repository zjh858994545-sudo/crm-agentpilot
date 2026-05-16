package com.agentpilot.operations.vo;

import java.time.LocalDateTime;
import java.util.List;

public record LaunchReadinessStatus(
        String overallStatus,
        String phase,
        LocalDateTime checkedAt,
        long passCount,
        long warnCount,
        long failCount,
        List<LaunchReadinessCheck> checks
) {
}
