package com.agentpilot.operations.service;

import com.agentpilot.security.CurrentUser;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminAuditService {
    private final JdbcTemplate jdbcTemplate;

    public record AdminAuditLog(
            Long id,
            String tenantId,
            Long actorUserId,
            String action,
            String targetType,
            String targetId,
            String summary,
            String traceId,
            LocalDateTime createdAt
    ) {
    }

    public AdminAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String action, String targetType, String targetId, String summary) {
        jdbcTemplate.update(
                """
                        INSERT INTO agentpilot_admin_audit_log (
                            tenant_id, actor_user_id, action, target_type, target_id, summary, trace_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                CurrentUser.tenantId(),
                CurrentUser.userId(),
                requireText(action, "action"),
                requireText(targetType, "targetType"),
                requireText(targetId, "targetId"),
                requireText(summary, "summary"),
                MDC.get("traceId")
        );
    }

    public List<AdminAuditLog> recent(String tenantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
                """
                        SELECT id,
                               tenant_id,
                               actor_user_id,
                               action,
                               target_type,
                               target_id,
                               summary,
                               trace_id,
                               created_at
                        FROM agentpilot_admin_audit_log
                        WHERE tenant_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new AdminAuditLog(
                        rs.getLong("id"),
                        rs.getString("tenant_id"),
                        rs.getLong("actor_user_id"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("summary"),
                        rs.getString("trace_id"),
                        rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()
                ),
                tenantId,
                safeLimit
        );
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
