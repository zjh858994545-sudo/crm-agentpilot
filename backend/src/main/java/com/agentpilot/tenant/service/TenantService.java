package com.agentpilot.tenant.service;

import com.agentpilot.tenant.entity.AgentPilotTenant;
import com.agentpilot.tenant.mapper.AgentPilotTenantMapper;
import com.agentpilot.tenant.vo.TenantUpsertRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TenantService extends ServiceImpl<AgentPilotTenantMapper, AgentPilotTenant> {
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}$");
    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED");

    public List<AgentPilotTenant> listTenants() {
        return list(new LambdaQueryWrapper<AgentPilotTenant>()
                .orderByAsc(AgentPilotTenant::getId));
    }

    @Transactional
    public AgentPilotTenant createTenant(TenantUpsertRequest request) {
        String id = normalizeTenantId(request == null ? null : request.id());
        if (getById(id) != null) {
            throw new IllegalArgumentException("Tenant already exists: " + id);
        }
        AgentPilotTenant tenant = new AgentPilotTenant();
        tenant.setId(id);
        tenant.setName(requiredText(request == null ? null : request.name(), "tenant name"));
        tenant.setPlanCode(defaultText(request == null ? null : request.planCode(), "standard"));
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        save(tenant);
        return tenant;
    }

    @Transactional
    public AgentPilotTenant updateTenant(String tenantId, TenantUpsertRequest request) {
        AgentPilotTenant tenant = requireTenant(tenantId);
        tenant.setName(requiredText(request == null ? null : request.name(), "tenant name"));
        tenant.setPlanCode(defaultText(request == null ? null : request.planCode(), tenant.getPlanCode()));
        tenant.setUpdatedAt(LocalDateTime.now());
        updateById(tenant);
        return tenant;
    }

    @Transactional
    public AgentPilotTenant changeStatus(String tenantId, String status) {
        AgentPilotTenant tenant = requireTenant(tenantId);
        String normalizedStatus = normalizeStatus(status);
        if ("DISABLED".equals(normalizedStatus)
                && "ACTIVE".equals(tenant.getStatus())
                && activeTenantCount() <= 1) {
            throw new IllegalArgumentException("Cannot disable the last active tenant");
        }
        tenant.setStatus(normalizedStatus);
        tenant.setUpdatedAt(LocalDateTime.now());
        updateById(tenant);
        return tenant;
    }

    private AgentPilotTenant requireTenant(String tenantId) {
        AgentPilotTenant tenant = getById(normalizeTenantId(tenantId));
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }
        return tenant;
    }

    private long activeTenantCount() {
        return count(new LambdaQueryWrapper<AgentPilotTenant>()
                .eq(AgentPilotTenant::getStatus, "ACTIVE"));
    }

    private String normalizeTenantId(String tenantId) {
        String id = requiredText(tenantId, "tenant id");
        if (!TENANT_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Tenant id must be 2-64 chars and contain only letters, numbers, '_' or '-'");
        }
        return id;
    }

    private String normalizeStatus(String status) {
        String normalized = requiredText(status, "tenant status").toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new IllegalArgumentException("Tenant status must be ACTIVE or DISABLED");
        }
        return normalized;
    }

    private String requiredText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
