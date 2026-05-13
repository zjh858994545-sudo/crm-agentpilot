package com.agentpilot.scoring.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LeadRecommendation(
        Long leadId,
        Long customerId,
        String customerName,
        String industry,
        BigDecimal estimatedAmount,
        LocalDate expectedCloseDate,
        double score,
        String priority,
        List<String> reasons,
        String suggestedAction
) {
}

