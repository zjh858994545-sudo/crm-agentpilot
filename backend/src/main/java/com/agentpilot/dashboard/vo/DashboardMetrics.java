package com.agentpilot.dashboard.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardMetrics(
        Long salesRepId,
        OffsetDateTime generatedAt,
        DashboardSummary summary,
        List<DashboardTrendPoint> leadTrend,
        DashboardRiskHeatmap riskHeatmap
) {
}
