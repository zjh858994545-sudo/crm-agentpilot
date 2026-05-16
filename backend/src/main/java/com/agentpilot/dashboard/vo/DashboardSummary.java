package com.agentpilot.dashboard.vo;

import java.math.BigDecimal;

public record DashboardSummary(
        int highLeadCount,
        BigDecimal highLeadAmount,
        int riskCustomerCount,
        int dueTaskCount,
        int renewalCustomerCount,
        int pendingConfirmationCount
) {
}
