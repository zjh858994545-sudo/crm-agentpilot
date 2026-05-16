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
        "agentpilot.retention.outbox-published-days=1"
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
}
