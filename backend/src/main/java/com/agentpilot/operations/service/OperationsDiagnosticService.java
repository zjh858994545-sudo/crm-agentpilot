package com.agentpilot.operations.service;

import com.agentpilot.events.service.OutboxEventService;
import com.agentpilot.operations.vo.LaunchReadinessStatus;
import com.agentpilot.operations.vo.RetentionStatus;
import com.agentpilot.operations.vo.UsageSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class OperationsDiagnosticService {
    private final LaunchReadinessService launchReadinessService;
    private final RetentionMaintenanceService retentionMaintenanceService;
    private final UsageMetricsService usageMetricsService;
    private final OutboxEventService outboxEventService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationsDiagnosticService(
            LaunchReadinessService launchReadinessService,
            RetentionMaintenanceService retentionMaintenanceService,
            UsageMetricsService usageMetricsService,
            OutboxEventService outboxEventService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.launchReadinessService = launchReadinessService;
        this.retentionMaintenanceService = retentionMaintenanceService;
        this.usageMetricsService = usageMetricsService;
        this.outboxEventService = outboxEventService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public byte[] buildBundle(String tenantId, Long actorUserId) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                writeJson(zip, "manifest.json", manifest(tenantId, actorUserId));
                writeJson(zip, "readiness.json", safe("readiness", launchReadinessService::status));
                writeJson(zip, "retention.json", safe("retention", retentionMaintenanceService::status));
                writeJson(zip, "usage.json", safe("usage", () -> usageMetricsService.snapshot(tenantId)));
                writeJson(zip, "outbox.json", outboxSummary());
                writeJson(zip, "database.json", databaseSummary());
                writeText(zip, "README.txt", """
                        CRM-AgentPilot diagnostic bundle

                        This bundle is intended for operations troubleshooting.
                        It contains runtime state, aggregate counts, readiness checks, retention status, usage metrics and outbox status.
                        It intentionally does not export customer records, contact logs, call transcripts, API tokens or model keys.
                        Share it with engineers only when support needs runtime context.

                        Files:
                        - manifest.json: generation time, tenant, actor, trace id and JVM/OS summary.
                        - readiness.json: production readiness checks and blocking risks.
                        - retention.json: cleanup categories, eligible rows, protected rows and cleanup policy.
                        - usage.json: tenant-scoped daily and seven-day usage counters.
                        - outbox.json: pending, dispatching and dead-letter event state.
                        - database.json: database product and safe aggregate table counts only.
                        """);
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to build diagnostic bundle", ex);
        }
    }

    private Map<String, Object> manifest(String tenantId, Long actorUserId) {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", OffsetDateTime.now().toString());
        result.put("tenantId", tenantId);
        result.put("actorUserId", actorUserId);
        result.put("traceId", MDC.get("traceId"));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("osName", System.getProperty("os.name"));
        result.put("availableProcessors", runtime.availableProcessors());
        result.put("maxMemoryMb", runtime.maxMemory() / 1024 / 1024);
        result.put("totalMemoryMb", runtime.totalMemory() / 1024 / 1024);
        result.put("freeMemoryMb", runtime.freeMemory() / 1024 / 1024);
        return result;
    }

    private Map<String, Object> outboxSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", outboxEventService.pendingCount());
        result.put("dispatching", outboxEventService.dispatchingCount());
        result.put("deadLetters", outboxEventService.deadLetterCount());
        result.put("maxRetryCount", OutboxEventService.MAX_RETRY_COUNT);
        result.put("dispatchLockTtlSeconds", OutboxEventService.DISPATCH_LOCK_TTL.toSeconds());
        result.put("recentDeadLetters", outboxEventService.recentDeadLetters(20));
        return result;
    }

    private Map<String, Object> databaseSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseProduct", safe("databaseProduct", this::databaseProductName));
        result.put("selectedTableCounts", safe("tableCounts", this::selectedTableCounts));
        return result;
    }

    private String databaseProductName() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        }
    }

    private List<Map<String, Object>> selectedTableCounts() {
        List<String> tables = List.of(
                "crm_customer",
                "crm_lead",
                "crm_task",
                "crm_contact_log",
                "crm_agent_run",
                "crm_agent_tool_call",
                "crm_agent_confirmation",
                "crm_retrieval_log",
                "agent_outbox_event",
                "agentpilot_tenant",
                "agentpilot_tenant_config",
                "agentpilot_export_request",
                "agentpilot_admin_audit_log"
        );
        return tables.stream()
                .map(table -> Map.<String, Object>of(
                        "table", table,
                        "rows", safe("count-" + table, () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class))
                ))
                .toList();
    }

    private <T> Object safe(String name, CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            return Map.of("error", name + " unavailable", "message", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        writeText(zip, name, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }

    private void writeText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
