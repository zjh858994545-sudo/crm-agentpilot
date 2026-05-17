package com.agentpilot.operations.service;

import com.agentpilot.operations.vo.UsageMetric;
import com.agentpilot.operations.vo.UsageSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsageMetricsService {
    private static final String AGENT_RUN_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_agent_run
            WHERE tenant_id = ?
              AND created_at >= ?
            """;
    private static final String TOOL_CALL_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_agent_tool_call tool_call
            JOIN crm_agent_run agent_run ON agent_run.id = tool_call.run_id
            WHERE agent_run.tenant_id = ?
              AND tool_call.created_at >= ?
            """;
    private static final String CONFIRMATION_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_agent_confirmation confirmation
            JOIN crm_agent_run agent_run ON agent_run.id = confirmation.run_id
            WHERE agent_run.tenant_id = ?
              AND confirmation.created_at >= ?
            """;
    private static final String NOTIFICATION_COUNT_SQL = """
            SELECT COUNT(*)
            FROM agentpilot_notification
            WHERE tenant_id = ?
              AND created_at >= ?
            """;
    private static final String OUTBOX_COUNT_SQL = """
            SELECT COUNT(*)
            FROM agent_outbox_event
            WHERE tenant_id = ?
              AND created_at >= ?
            """;
    private static final String REAL_MODEL_RUN_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_agent_run
            WHERE tenant_id = ?
              AND created_at >= ?
              AND model_name IS NOT NULL
              AND LOWER(model_name) NOT LIKE '%mock%'
            """;
    private static final String AVERAGE_AGENT_LATENCY_SQL = """
            SELECT COALESCE(AVG(latency_ms), 0)
            FROM crm_agent_run
            WHERE tenant_id = ?
              AND created_at >= ?
              AND latency_ms IS NOT NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public UsageMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UsageSnapshot snapshot(String tenantId) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime sevenDaysStart = today.minusDays(6).atStartOfDay();
        return new UsageSnapshot(
                tenantId,
                today,
                LocalDateTime.now(),
                List.of(
                        countMetric("agentRuns", "Agent 会话", AGENT_RUN_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "次", "用户发起的一次 AI 处理请求"),
                        countMetric("toolCalls", "工具调用", TOOL_CALL_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "次", "Agent 调用客户、知识库、商机和写入工具的次数"),
                        countMetric("confirmations", "写入确认单", CONFIRMATION_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "张", "所有 CRM 写操作确认单，包含待确认、已确认和已拒绝"),
                        countMetric("notifications", "确认提醒", NOTIFICATION_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "条", "站内通知以及外部推送前的通知记录"),
                        countMetric("outboxEvents", "Outbox 事件", OUTBOX_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "条", "等待或已经分发到 log/Kafka 的业务事件"),
                        countMetric("realModelRuns", "真实模型调用路径", REAL_MODEL_RUN_COUNT_SQL, tenantId, todayStart, sevenDaysStart, "次", "model_name 非 mock 的 Agent run，用于估算外部模型使用量"),
                        latencyMetric("avgAgentLatency", "Agent 平均耗时", tenantId, todayStart, sevenDaysStart)
                )
        );
    }

    private UsageMetric countMetric(
            String key,
            String name,
            String sql,
            String tenantId,
            LocalDateTime todayStart,
            LocalDateTime sevenDaysStart,
            String unit,
            String note
    ) {
        return new UsageMetric(
                key,
                name,
                longValue(sql, tenantId, todayStart),
                longValue(sql, tenantId, sevenDaysStart),
                unit,
                note
        );
    }

    private UsageMetric latencyMetric(String key, String name, String tenantId, LocalDateTime todayStart, LocalDateTime sevenDaysStart) {
        return new UsageMetric(
                key,
                name,
                Math.round(doubleValue(AVERAGE_AGENT_LATENCY_SQL, tenantId, todayStart)),
                Math.round(doubleValue(AVERAGE_AGENT_LATENCY_SQL, tenantId, sevenDaysStart)),
                "ms",
                "按 tenant 聚合的 Agent run 平均 latency_ms，用于观察模型和工具链路性能"
        );
    }

    private long longValue(String sql, Object... args) {
        Number number = jdbcTemplate.queryForObject(sql, Number.class, args);
        return number == null ? 0L : number.longValue();
    }

    private double doubleValue(String sql, Object... args) {
        Number number = jdbcTemplate.queryForObject(sql, Number.class, args);
        return number == null ? 0.0 : number.doubleValue();
    }
}
