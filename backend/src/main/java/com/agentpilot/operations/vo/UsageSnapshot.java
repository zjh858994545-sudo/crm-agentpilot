package com.agentpilot.operations.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UsageSnapshot(
        String tenantId,
        LocalDate businessDate,
        LocalDateTime generatedAt,
        List<UsageMetric> metrics
) {
}
