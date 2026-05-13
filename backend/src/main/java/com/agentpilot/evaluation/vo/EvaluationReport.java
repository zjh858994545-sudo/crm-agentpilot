package com.agentpilot.evaluation.vo;

import java.util.List;

public record EvaluationReport(
        String reportName,
        String generatedAt,
        List<EvaluationMetric> metrics,
        String reportPath
) {
}

