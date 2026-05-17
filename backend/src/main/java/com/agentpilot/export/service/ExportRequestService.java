package com.agentpilot.export.service;

import com.agentpilot.export.entity.ExportRequest;
import com.agentpilot.export.mapper.ExportRequestMapper;
import com.agentpilot.export.vo.ExportRequestCreateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ExportRequestService extends ServiceImpl<ExportRequestMapper, ExportRequest> {
    private static final Set<String> ALLOWED_EXPORT_TYPES = Set.of(
            "CUSTOMERS",
            "LEADS",
            "CONTACT_LOGS",
            "AGENT_AUDIT",
            "KNOWLEDGE_DOCS"
    );

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
        return decide(id, tenantId, approverUserId, "APPROVED", comment);
    }

    @Transactional
    public ExportRequest reject(Long id, String tenantId, Long approverUserId, String comment) {
        return decide(id, tenantId, approverUserId, "REJECTED", comment);
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
