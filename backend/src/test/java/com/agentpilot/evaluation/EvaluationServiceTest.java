package com.agentpilot.evaluation;

import com.agentpilot.evaluation.service.EvaluationService;
import com.agentpilot.evaluation.vo.EvaluationReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EvaluationServiceTest {

    @Autowired
    private EvaluationService evaluationService;

    @Test
    void evaluationGeneratesRealMetricsAndReportPath() {
        EvaluationReport report = evaluationService.runEvaluation();

        assertThat(report.metrics()).isNotEmpty();
        assertThat(report.metrics()).anyMatch(metric -> metric.name().equals("RAG Recall@5"));
        assertThat(report.reportPath()).contains("report-");
    }
}
