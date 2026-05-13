package com.agentpilot.evaluation.service;

import com.agentpilot.agent.orchestrator.AgentOrchestrator;
import com.agentpilot.agent.vo.AgentChatRequest;
import com.agentpilot.agent.vo.AgentChatResponse;
import com.agentpilot.evaluation.vo.EvaluationMetric;
import com.agentpilot.evaluation.vo.EvaluationReport;
import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.rag.vo.KnowledgeSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class EvaluationService {
    private final RagService ragService;
    private final AgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public EvaluationService(RagService ragService, AgentOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    public EvaluationReport runEvaluation() {
        Instant startedAt = Instant.now();
        List<JsonNode> ragCases = readJsonl("../eval/rag_questions.jsonl");
        List<JsonNode> toolCases = readJsonl("../eval/tool_call_cases.jsonl");
        List<Long> latencies = new ArrayList<>();

        int ragHit = 0;
        int citationHit = 0;
        for (JsonNode testCase : ragCases) {
            Instant caseStarted = Instant.now();
            String question = testCase.get("question").asText();
            KnowledgeSearchResponse search = ragService.search(question, 5);
            KnowledgeAnswer answer = ragService.ask(question, 5);
            latencies.add(java.time.Duration.between(caseStarted, Instant.now()).toMillis());
            if (matchesAnyKeyword(search, testCase.get("expectedKeywords"))) {
                ragHit++;
            }
            if (!answer.citations().isEmpty()) {
                citationHit++;
            }
        }

        int toolHit = 0;
        int writeCases = 0;
        int confirmationCovered = 0;
        for (JsonNode testCase : toolCases) {
            Instant caseStarted = Instant.now();
            AgentChatResponse response = orchestrator.chat(new AgentChatRequest(
                    null,
                    1L,
                    1L,
                    null,
                    testCase.get("input").asText()
            ));
            latencies.add(java.time.Duration.between(caseStarted, Instant.now()).toMillis());
            if (containsExpectedTools(response, testCase.get("expectedTools"))) {
                toolHit++;
            }
            if (testCase.path("mustRequireConfirmation").asBoolean(false)) {
                writeCases++;
                if ("confirmation_required".equals(response.type()) && response.confirmationId() != null) {
                    confirmationCovered++;
                }
            }
        }

        KnowledgeAnswer refused = ragService.ask("请解释量子纠缠实验如何搭建？", 3);
        double refusalAccuracy = refused.refused() ? 1.0 : 0.0;

        List<EvaluationMetric> metrics = List.of(
                metric("RAG Recall@5", ratio(ragHit, ragCases.size()), "ratio"),
                metric("Citation Hit Rate", ratio(citationHit, ragCases.size()), "ratio"),
                metric("Refusal Accuracy", refusalAccuracy, "ratio"),
                metric("Tool Calling Success Rate", ratio(toolHit, toolCases.size()), "ratio"),
                metric("Write Confirmation Coverage", ratio(confirmationCovered, writeCases), "ratio"),
                metric("Average Latency", average(latencies), "ms"),
                metric("P95 Latency", percentile(latencies, 0.95), "ms")
        );
        String reportPath = writeReport(metrics, startedAt);
        return new EvaluationReport("CRM-AgentPilot Evaluation", Instant.now().toString(), metrics, reportPath);
    }

    private boolean matchesAnyKeyword(KnowledgeSearchResponse search, JsonNode keywords) {
        for (JsonNode keyword : keywords) {
            String value = keyword.asText();
            boolean matched = search.items().stream().anyMatch(item ->
                    item.content().contains(value) || item.docTitle().contains(value) || item.docType().contains(value));
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExpectedTools(AgentChatResponse response, JsonNode expectedTools) {
        for (JsonNode expected : expectedTools) {
            boolean matched = response.toolCalls().stream()
                    .anyMatch(call -> call.toolName().equals(expected.asText()));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private List<JsonNode> readJsonl(String path) {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                    .filter(line -> !line.isBlank())
                    .map(this::readJson)
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSONL line", ex);
        }
    }

    private EvaluationMetric metric(String name, double value, String unit) {
        return new EvaluationMetric(name, Math.round(value * 10000.0) / 10000.0, unit);
    }

    private double ratio(int hit, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (double) hit / total;
    }

    private double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private String writeReport(List<EvaluationMetric> metrics, Instant startedAt) {
        String fileName = "report-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".md";
        String report = tryWriteReport(Path.of("../eval/reports"), fileName, metrics, startedAt);
        if (!report.isBlank()) {
            return report;
        }
        return tryWriteReport(Path.of("target/eval-reports"), fileName, metrics, startedAt);
    }

    private String tryWriteReport(
            Path reportsDir,
            String fileName,
            List<EvaluationMetric> metrics,
            Instant startedAt
    ) {
        try {
            Files.createDirectories(reportsDir);
            Path report = reportsDir.resolve(fileName);
            List<String> lines = new ArrayList<>();
            lines.add("# CRM-AgentPilot Evaluation Report");
            lines.add("");
            lines.add("- Started at: " + startedAt);
            lines.add("- Generated at: " + Instant.now());
            lines.add("");
            lines.add("| Metric | Value | Unit |");
            lines.add("|---|---:|---|");
            for (EvaluationMetric metric : metrics) {
                lines.add("| " + metric.name() + " | " + metric.value() + " | " + metric.unit() + " |");
            }
            Files.write(report, lines, StandardCharsets.UTF_8);
            return report.normalize().toString();
        } catch (IOException ex) {
            return "";
        }
    }
}
