package com.agentpilot.dashboard.vo;

import java.math.BigDecimal;

public record DashboardTrendPoint(
        String date,
        BigDecimal amount,
        int high,
        int total
) {
}
