package com.agentpilot.dashboard.vo;

import java.math.BigDecimal;

public record DashboardTeamMemberMetric(
        Long salesRepId,
        String salesRepName,
        String teamName,
        int openLeadCount,
        int highLeadCount,
        BigDecimal openLeadAmount,
        int riskCustomerCount,
        int dueTaskCount,
        int pendingConfirmationCount
) {
}
