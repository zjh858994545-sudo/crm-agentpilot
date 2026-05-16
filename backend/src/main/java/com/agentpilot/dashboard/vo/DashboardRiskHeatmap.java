package com.agentpilot.dashboard.vo;

import java.util.List;

public record DashboardRiskHeatmap(
        List<String> industries,
        List<String> riskLevels,
        int max,
        List<DashboardRiskCell> cells
) {
}
