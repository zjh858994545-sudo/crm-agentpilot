package com.agentpilot.operations;

import com.agentpilot.operations.service.RetentionMaintenanceService;
import com.agentpilot.operations.vo.RetentionCleanupResult;
import com.agentpilot.operations.vo.RetentionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agentpilot.retention.enabled=true",
        "agentpilot.retention.agent-audit-days=1",
        "agentpilot.retention.retrieval-log-days=1",
        "agentpilot.retention.outbox-published-days=1",
        "agentpilot.retention.export-artifact-days=3"
})
@ActiveProfiles("test")
@Transactional
class RetentionMaintenanceServiceTest {
    @Autowired
    private RetentionMaintenanceService retentionMaintenanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dryRunEstimatesRowsWithoutDeletingData() {
        seedOldOperationalRows(991001L, false);

        RetentionCleanupResult result = retentionMaintenanceService.cleanup(true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.totalEligibleRows()).isGreaterThanOrEqualTo(5);
        assertThat(result.totalDeletedRows()).isZero();
        assertThat(count("select count(*) from crm_agent_run where id = 991001")).isEqualTo(1);
    }

    @Test
    void cleanupDeletesEligibleRowsAndKeepsPendingConfirmations() {
        seedOldOperationalRows(991101L, false);
        seedOldOperationalRows(991201L, true);

        RetentionCleanupResult result = retentionMaintenanceService.cleanup(false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.totalDeletedRows()).isGreaterThanOrEqualTo(5);
        assertThat(count("select count(*) from crm_agent_run where id = 991101")).isZero();
        assertThat(count("select count(*) from crm_retrieval_log where id = 991101")).isZero();
        assertThat(count("select count(*) from agent_outbox_event where id = 991101")).isZero();
        assertThat(count("select count(*) from crm_agent_run where id = 991201")).isEqualTo(1);
        assertThat(count("select count(*) from crm_agent_confirmation where id = 991201")).isEqualTo(1);
    }

    @Test
    void statusShowsProtectedRowsForPendingConfirmations() {
        seedOldOperationalRows(991301L, true);

        RetentionStatus status = retentionMaintenanceService.status();

        assertThat(status.categories())
                .filteredOn(category -> category.key().equals("agent-workflow-audit"))
                .singleElement()
                .satisfies(category -> assertThat(category.protectedRows()).isGreaterThanOrEqualTo(1));
    }

    @Test
    void cleanupExpiresExportArtifactsWithoutDeletingApprovalRecord() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into agentpilot_export_request (
                    id, tenant_id, requester_user_id, export_type, reason, status, approver_user_id,
                    approval_comment, file_name, file_content, file_size_bytes, expires_at,
                    download_count, requested_at, decided_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                991401L, "demo", 1L, "customers", "retention test", "APPROVED", 3L,
                "approved", "customers.csv", "customer_name,mobile\n测试客户,139****0000\n", 43L,
                Timestamp.valueOf(now.minusDays(1)), 0,
                Timestamp.valueOf(now.minusDays(4)), Timestamp.valueOf(now.minusDays(4)));

        RetentionCleanupResult result = retentionMaintenanceService.cleanup(false);

        assertThat(result.categories())
                .filteredOn(category -> category.key().equals("export-artifact"))
                .singleElement()
                .satisfies(category -> {
                    assertThat(category.eligibleRows()).isEqualTo(1);
                    assertThat(category.deletedRows()).isEqualTo(1);
                });
        assertThat(count("select count(*) from agentpilot_export_request where id = 991401")).isEqualTo(1);
        assertThat(value("select status from agentpilot_export_request where id = 991401")).isEqualTo("EXPIRED");
        assertThat(value("select file_content from agentpilot_export_request where id = 991401")).isNull();
    }

    private void seedOldOperationalRows(long id, boolean pendingConfirmation) {
        LocalDateTime oldTime = LocalDateTime.now().minusDays(30);
        Timestamp oldTimestamp = Timestamp.valueOf(oldTime);
        jdbcTemplate.update("""
                insert into crm_agent_run (
                    id, session_id, user_id, sales_rep_id, user_input, agent_output, intent, status, model_name, created_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, id, 1L, 1L, "retention test", "done", "TEST", "COMPLETED", "mock-router", oldTimestamp, oldTimestamp);
        jdbcTemplate.update("""
                insert into crm_agent_tool_call (
                    id, run_id, tool_name, tool_type, input_json, output_json, status, latency_ms, requires_confirmation, created_at, completed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, id, "rankLeads", "READ", "{}", "{}", "SUCCESS", 12L, false, oldTimestamp, oldTimestamp);
        jdbcTemplate.update("""
                insert into crm_agent_confirmation (
                    id, run_id, action_type, action_summary, payload_json, status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """, id, id, "CREATE_TASK", "retention test", "{}", pendingConfirmation ? "PENDING" : "CONFIRMED", oldTimestamp);
        jdbcTemplate.update("""
                insert into crm_retrieval_log (
                    id, run_id, query, rewritten_query, retriever_type, top_k, result_json, latency_ms, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, id, "retention", "retention", "hybrid", 5, "[]", 8L, oldTimestamp);
        jdbcTemplate.update("""
                insert into agent_outbox_event (
                    id, event_id, topic, event_type, aggregate_type, aggregate_id, payload_json, status, retry_count, created_at, published_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, "retention-" + id, "agent-run-events", "retention.test", "agent_run", String.valueOf(id), "{}", "PUBLISHED", 0, oldTimestamp, oldTimestamp);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String value(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }
}
