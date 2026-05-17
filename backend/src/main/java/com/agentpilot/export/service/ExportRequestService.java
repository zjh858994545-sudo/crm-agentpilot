package com.agentpilot.export.service;

import com.agentpilot.export.entity.ExportRequest;
import com.agentpilot.export.mapper.ExportRequestMapper;
import com.agentpilot.export.vo.ExportRequestCreateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

@Service
public class ExportRequestService extends ServiceImpl<ExportRequestMapper, ExportRequest> {
    private static final Set<String> ALLOWED_EXPORT_TYPES = Set.of(
            "CUSTOMERS",
            "LEADS",
            "CONTACT_LOGS",
            "AGENT_AUDIT",
            "KNOWLEDGE_DOCS"
    );
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbcTemplate;

    public record ExportDownload(
            String fileName,
            byte[] content,
            String contentType
    ) {
    }

    private record ExportArtifact(
            String fileName,
            String content
    ) {
        long sizeBytes() {
            return content.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    public ExportRequestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExportRequest> listByTenant(String tenantId, String status, int limit) {
        LambdaQueryWrapper<ExportRequest> query = new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getTenantId, requireText(tenantId, "tenantId"))
                .orderByDesc(ExportRequest::getRequestedAt)
                .last("limit " + Math.max(1, Math.min(limit, 200)));
        if (StringUtils.hasText(status)) {
            query.eq(ExportRequest::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return list(query);
    }

    @Transactional
    public ExportRequest create(String tenantId, Long requesterUserId, ExportRequestCreateRequest request) {
        ExportRequest exportRequest = new ExportRequest();
        exportRequest.setTenantId(requireText(tenantId, "tenantId"));
        exportRequest.setRequesterUserId(requesterUserId);
        exportRequest.setExportType(normalizeExportType(request == null ? null : request.exportType()));
        exportRequest.setReason(requireText(request == null ? null : request.reason(), "reason"));
        exportRequest.setStatus("PENDING");
        exportRequest.setRequestedAt(LocalDateTime.now());
        save(exportRequest);
        return exportRequest;
    }

    @Transactional
    public ExportRequest approve(Long id, String tenantId, Long approverUserId, String comment) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        ExportRequest pending = getOne(new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getId, id)
                .eq(ExportRequest::getTenantId, normalizedTenantId)
                .eq(ExportRequest::getStatus, "PENDING"), false);
        if (pending == null) {
            throw new IllegalStateException("Export request is not pending or outside current tenant");
        }
        ExportArtifact artifact = generateArtifact(normalizedTenantId, pending.getExportType(), id);
        LocalDateTime now = LocalDateTime.now();
        boolean updated = update(new LambdaUpdateWrapper<ExportRequest>()
                .eq(ExportRequest::getId, id)
                .eq(ExportRequest::getTenantId, normalizedTenantId)
                .eq(ExportRequest::getStatus, "PENDING")
                .set(ExportRequest::getStatus, "APPROVED")
                .set(ExportRequest::getApproverUserId, approverUserId)
                .set(ExportRequest::getApprovalComment, StringUtils.hasText(comment) ? comment.trim() : "")
                .set(ExportRequest::getFileName, artifact.fileName())
                .set(ExportRequest::getFileContent, artifact.content())
                .set(ExportRequest::getFileSizeBytes, artifact.sizeBytes())
                .set(ExportRequest::getExpiresAt, now.plusDays(3))
                .set(ExportRequest::getDecidedAt, now));
        if (!updated) {
            throw new IllegalStateException("Export request is not pending or outside current tenant");
        }
        return getById(id);
    }

    @Transactional
    public ExportRequest reject(Long id, String tenantId, Long approverUserId, String comment) {
        return decide(id, tenantId, approverUserId, "REJECTED", comment);
    }

    @Transactional
    public ExportDownload download(Long id, String tenantId, Long userId, boolean canApprove) {
        ExportRequest request = getOne(new LambdaQueryWrapper<ExportRequest>()
                .eq(ExportRequest::getId, id)
                .eq(ExportRequest::getTenantId, requireText(tenantId, "tenantId")), false);
        if (request == null) {
            throw new IllegalStateException("Export request is outside current tenant");
        }
        if (!canApprove && !request.getRequesterUserId().equals(userId)) {
            throw new AccessDeniedException("export request belongs to another user");
        }
        if (!"APPROVED".equals(request.getStatus())) {
            throw new IllegalStateException("Export request is not approved");
        }
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            update(new LambdaUpdateWrapper<ExportRequest>()
                    .eq(ExportRequest::getId, id)
                    .eq(ExportRequest::getTenantId, tenantId)
                    .eq(ExportRequest::getStatus, "APPROVED")
                    .set(ExportRequest::getStatus, "EXPIRED"));
            throw new IllegalStateException("Export file has expired");
        }
        if (!StringUtils.hasText(request.getFileContent())) {
            throw new IllegalStateException("Export file is not generated");
        }
        update(new LambdaUpdateWrapper<ExportRequest>()
                .eq(ExportRequest::getId, id)
                .eq(ExportRequest::getTenantId, tenantId)
                .setSql("download_count = download_count + 1")
                .set(ExportRequest::getDownloadedAt, LocalDateTime.now()));
        String content = "\uFEFF" + request.getFileContent();
        return new ExportDownload(
                StringUtils.hasText(request.getFileName()) ? request.getFileName() : "agentpilot-export-" + id + ".csv",
                content.getBytes(StandardCharsets.UTF_8),
                "text/csv; charset=UTF-8"
        );
    }

    private ExportRequest decide(Long id, String tenantId, Long approverUserId, String status, String comment) {
        boolean updated = update(new LambdaUpdateWrapper<ExportRequest>()
                .eq(ExportRequest::getId, id)
                .eq(ExportRequest::getTenantId, requireText(tenantId, "tenantId"))
                .eq(ExportRequest::getStatus, "PENDING")
                .set(ExportRequest::getStatus, status)
                .set(ExportRequest::getApproverUserId, approverUserId)
                .set(ExportRequest::getApprovalComment, StringUtils.hasText(comment) ? comment.trim() : "")
                .set(ExportRequest::getDecidedAt, LocalDateTime.now()));
        if (!updated) {
            throw new IllegalStateException("Export request is not pending or outside current tenant");
        }
        return getById(id);
    }

    private ExportArtifact generateArtifact(String tenantId, String exportType, Long requestId) {
        String normalizedType = normalizeExportType(exportType);
        String fileName = "agentpilot-" + normalizedType.toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                + requestId + "-" + LocalDateTime.now().format(FILE_TIME_FORMAT) + ".csv";
        List<String> headers = headers(normalizedType);
        List<Map<String, Object>> rows = queryRows(tenantId, normalizedType);
        StringBuilder content = new StringBuilder();
        appendCsvLine(content, headers);
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                values.add(stringValue(row, header));
            }
            appendCsvLine(content, values);
        }
        return new ExportArtifact(fileName, content.toString());
    }

    private List<String> headers(String exportType) {
        return switch (exportType) {
            case "CUSTOMERS" -> List.of("id", "name", "industry", "city", "contact_name", "contact_mobile_masked", "lifecycle_stage", "value_level", "risk_level", "owner_sales_rep_id", "last_contact_at");
            case "LEADS" -> List.of("id", "customer_id", "sales_rep_id", "source", "stage", "intent_level", "estimated_amount", "score", "status", "expected_close_date");
            case "CONTACT_LOGS" -> List.of("id", "customer_id", "sales_rep_id", "lead_id", "channel", "summary", "customer_intent", "objections", "next_action", "contact_at");
            case "AGENT_AUDIT" -> List.of("id", "session_id", "user_id", "sales_rep_id", "customer_id", "intent", "status", "model_name", "latency_ms", "completed_at");
            case "KNOWLEDGE_DOCS" -> List.of("id", "title", "doc_type", "source", "status", "created_by", "created_at", "updated_at");
            default -> throw new IllegalArgumentException("Unsupported exportType: " + exportType);
        };
    }

    private List<Map<String, Object>> queryRows(String tenantId, String exportType) {
        return switch (exportType) {
            case "CUSTOMERS" -> jdbcTemplate.queryForList("""
                    SELECT id,
                           name,
                           industry,
                           city,
                           contact_name,
                           contact_mobile AS contact_mobile_masked,
                           lifecycle_stage,
                           value_level,
                           risk_level,
                           owner_sales_rep_id,
                           last_contact_at
                    FROM crm_customer
                    WHERE tenant_id = ?
                    ORDER BY id
                    LIMIT ?
                    """, tenantId, MAX_EXPORT_ROWS).stream().map(this::maskCustomerRow).toList();
            case "LEADS" -> jdbcTemplate.queryForList("""
                    SELECT id, customer_id, sales_rep_id, source, stage, intent_level, estimated_amount, score, status, expected_close_date
                    FROM crm_lead
                    WHERE tenant_id = ?
                    ORDER BY id
                    LIMIT ?
                    """, tenantId, MAX_EXPORT_ROWS);
            case "CONTACT_LOGS" -> jdbcTemplate.queryForList("""
                    SELECT id, customer_id, sales_rep_id, lead_id, channel, summary, customer_intent, objections, next_action, contact_at
                    FROM crm_contact_log
                    WHERE tenant_id = ?
                    ORDER BY contact_at DESC, id DESC
                    LIMIT ?
                    """, tenantId, MAX_EXPORT_ROWS);
            case "AGENT_AUDIT" -> jdbcTemplate.queryForList("""
                    SELECT id, session_id, user_id, sales_rep_id, customer_id, intent, status, model_name, latency_ms, completed_at
                    FROM crm_agent_run
                    WHERE tenant_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                    """, tenantId, MAX_EXPORT_ROWS);
            case "KNOWLEDGE_DOCS" -> jdbcTemplate.queryForList("""
                    SELECT id, title, doc_type, source, status, created_by, created_at, updated_at
                    FROM crm_knowledge_doc
                    WHERE tenant_id = ?
                    ORDER BY id
                    LIMIT ?
                    """, tenantId, MAX_EXPORT_ROWS);
            default -> throw new IllegalArgumentException("Unsupported exportType: " + exportType);
        };
    }

    private Map<String, Object> maskCustomerRow(Map<String, Object> row) {
        String key = row.containsKey("contact_mobile_masked") ? "contact_mobile_masked" : "CONTACT_MOBILE_MASKED";
        Object mobile = row.get(key);
        row.put(key, maskMobile(mobile == null ? "" : String.valueOf(mobile)));
        return row;
    }

    private String maskMobile(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private void appendCsvLine(StringBuilder content, List<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        values.forEach(value -> joiner.add(csvEscape(value)));
        content.append(joiner).append('\n');
    }

    private String csvEscape(String value) {
        String text = value == null ? "" : value;
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeExportType(String exportType) {
        String normalized = requireText(exportType, "exportType").trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_EXPORT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported exportType: " + exportType);
        }
        return normalized;
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
