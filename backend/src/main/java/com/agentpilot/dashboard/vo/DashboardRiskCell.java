package com.agentpilot.dashboard.vo;

public record DashboardRiskCell(
        String industry,
        String riskLevel,
        int count
) {
}
