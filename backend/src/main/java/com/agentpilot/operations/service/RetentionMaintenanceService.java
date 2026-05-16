package com.agentpilot.operations.service;

import com.agentpilot.operations.config.RetentionProperties;
import com.agentpilot.operations.vo.RetentionCategoryResult;
import com.agentpilot.operations.vo.RetentionCategoryStatus;
import com.agentpilot.operations.vo.RetentionCleanupResult;
import com.agentpilot.operations.vo.RetentionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RetentionMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(RetentionMaintenanceService.class);

    private static final String DELETABLE_RUNS = """
            select r.id
            from crm_agent_run r
            where r.created_at < ?
              and r.status in ('COMPLETED', 'FAILED')
              and not exists (
                  select 1
                  from crm_agent_confirmation pending
                  where pending.run_id = r.id
                    and pending.status in ('PENDING', 'PROCESSING')
              )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RetentionProperties properties;

    public RetentionMaintenanceService(JdbcTemplate jdbcTemplate, RetentionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public RetentionStatus status() {
        List<RetentionCategoryStatus> categories = categories();
        return new RetentionStatus(
                properties.isEnabled(),
                properties.isScheduledCleanupEnabled(),
                properties.getCleanupCron(),
                properties.getMaxDeleteRowsPerRun(),
                LocalDateTime.now(),
                categories.stream().mapToLong(RetentionCategoryStatus::eligibleRows).sum(),
                categories
        );
    }

    @Transactional
    public RetentionCleanupResult cleanup(boolean dryRun) {
        List<RetentionCategoryStatus> estimates = categories();
        long totalEligibleRows = estimates.stream().mapToLong(RetentionCategoryStatus::eligibleRows).sum();
        if (!dryRun && !properties.isEnabled()) {
            throw new IllegalStateException("Retention cleanup is disabled. Set AGENTPILOT_RETENTION_ENABLED=true before deleting data.");
        }
        if (!dryRun && totalEligibleRows > properties.getMaxDeleteRowsPerRun()) {
            throw new IllegalStateException("Retention cleanup refused to delete " + totalEligibleRows
                    + " rows in one run. Increase AGENTPILOT_RETENTION_MAX_DELETE_ROWS_PER_RUN after backup verification.");
        }

        List<RetentionCategoryResult> results = new ArrayList<>();
        for (RetentionCategoryStatus estimate : estimates) {
            long deletedRows = dryRun ? 0 : deleteCategory(estimate.key(), estimate.cutoffAt());
            results.add(new RetentionCategoryResult(
                    estimate.key(),
                    estimate.name(),
                    estimate.retentionDays(),
                    estimate.cutoffAt(),
                    estimate.eligibleRows(),
                    deletedRows,
                    estimate.protectedRows()
            ));
        }
        long totalDeletedRows = results.stream().mapToLong(RetentionCategoryResult::deletedRows).sum();
        return new RetentionCleanupResult(dryRun, LocalDateTime.now(), totalEligibleRows, totalDeletedRows, results);
    }

    @Scheduled(cron = "${agentpilot.retention.cleanup-cron:0 30 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.isEnabled() || !properties.isScheduledCleanupEnabled()) {
            return;
        }
        try {
            RetentionCleanupResult result = cleanup(false);
            log.info("Retention cleanup completed: deletedRows={}, eligibleRows={}",
                    result.totalDeletedRows(), result.totalEligibleRows());
        } catch (RuntimeException ex) {
            log.warn("Retention cleanup failed: {}", ex.getMessage(), ex);
        }
    }

    private List<RetentionCategoryStatus> categories() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime agentCutoff = now.minusDays(properties.getAgentAuditDays());
        LocalDateTime retrievalCutoff = now.minusDays(properties.getRetrievalLogDays());
        LocalDateTime outboxCutoff = now.minusDays(properties.getOutboxPublishedDays());

        return List.of(
                new RetentionCategoryStatus(
                        "agent-workflow-audit",
                        "Agent 运行审计",
                        properties.getAgentAuditDays(),
                        agentCutoff,
                        countAgentWorkflow(agentCutoff),
                        countPendingConfirmations(agentCutoff),
                        "保留 PENDING / PROCESSING 确认单，避免清掉未处理写操作"
                ),
                new RetentionCategoryStatus(
                        "retrieval-log",
                        "知识检索日志",
                        properties.getRetrievalLogDays(),
                        retrievalCutoff,
                        count("select count(*) from crm_retrieval_log where created_at < ?", retrievalCutoff),
                        0,
                        "只清理历史检索日志，不删除知识库文档和知识片段"
                ),
                new RetentionCategoryStatus(
                        "outbox-published",
                        "已发布 Outbox 事件",
                        properties.getOutboxPublishedDays(),
                        outboxCutoff,
                        count("select count(*) from agent_outbox_event where status = 'PUBLISHED' and published_at < ?", outboxCutoff),
                        countOutboxProtected(outboxCutoff),
                        "保留 PENDING / FAILED / DISPATCHING / DEAD_LETTER，避免丢失待补偿事件"
                )
        );
    }

    private long countAgentWorkflow(LocalDateTime cutoffAt) {
        return count("select count(*) from crm_agent_confirmation where run_id in (" + DELETABLE_RUNS + ")", cutoffAt)
                + count("select count(*) from crm_agent_tool_call where run_id in (" + DELETABLE_RUNS + ")", cutoffAt)
                + count("select count(*) from crm_agent_run where id in (" + DELETABLE_RUNS + ")", cutoffAt);
    }

    private long countPendingConfirmations(LocalDateTime cutoffAt) {
        return count("""
                select count(*)
                from crm_agent_confirmation
                where created_at < ?
                  and status in ('PENDING', 'PROCESSING')
                """, cutoffAt);
    }

    private long countOutboxProtected(LocalDateTime cutoffAt) {
        return count("""
                select count(*)
                from agent_outbox_event
                where created_at < ?
                  and status in ('PENDING', 'FAILED', 'DISPATCHING', 'DEAD_LETTER')
                """, cutoffAt);
    }

    private long count(String sql, LocalDateTime cutoffAt) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.valueOf(cutoffAt));
        return value == null ? 0L : value;
    }

    private long deleteCategory(String key, LocalDateTime cutoffAt) {
        return switch (key) {
            case "agent-workflow-audit" -> deleteAgentWorkflow(cutoffAt);
            case "retrieval-log" -> jdbcTemplate.update(
                    "delete from crm_retrieval_log where created_at < ?",
                    Timestamp.valueOf(cutoffAt)
            );
            case "outbox-published" -> jdbcTemplate.update(
                    "delete from agent_outbox_event where status = 'PUBLISHED' and published_at < ?",
                    Timestamp.valueOf(cutoffAt)
            );
            default -> 0;
        };
    }

    private long deleteAgentWorkflow(LocalDateTime cutoffAt) {
        Timestamp cutoff = Timestamp.valueOf(cutoffAt);
        int confirmations = jdbcTemplate.update(
                "delete from crm_agent_confirmation where run_id in (" + DELETABLE_RUNS + ")",
                cutoff
        );
        int toolCalls = jdbcTemplate.update(
                "delete from crm_agent_tool_call where run_id in (" + DELETABLE_RUNS + ")",
                cutoff
        );
        int runs = jdbcTemplate.update(
                "delete from crm_agent_run where id in (" + DELETABLE_RUNS + ")",
                cutoff
        );
        return (long) confirmations + toolCalls + runs;
    }
}
