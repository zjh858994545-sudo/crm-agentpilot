package com.agentpilot.tenant.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.operations.service.AdminAuditService;
import com.agentpilot.tenant.entity.AgentPilotTenant;
import com.agentpilot.tenant.entity.TenantConfig;
import com.agentpilot.tenant.service.TenantConfigResolver;
import com.agentpilot.tenant.service.TenantConfigService;
import com.agentpilot.tenant.service.TenantService;
import com.agentpilot.tenant.vo.TenantConfigUpsertRequest;
import com.agentpilot.tenant.vo.TenantStatusRequest;
import com.agentpilot.tenant.vo.TenantUpsertRequest;
import com.agentpilot.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@PreAuthorize("hasAuthority('ops:read')")
public class TenantController {
    private final TenantService tenantService;
    private final TenantConfigService tenantConfigService;
    private final TenantConfigResolver tenantConfigResolver;
    private final AdminAuditService adminAuditService;

    public TenantController(
            TenantService tenantService,
            TenantConfigService tenantConfigService,
            TenantConfigResolver tenantConfigResolver,
            AdminAuditService adminAuditService
    ) {
        this.tenantService = tenantService;
        this.tenantConfigService = tenantConfigService;
        this.tenantConfigResolver = tenantConfigResolver;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public ApiResponse<List<AgentPilotTenant>> list() {
        return ApiResponse.ok(tenantService.listTenants());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> create(@RequestBody TenantUpsertRequest request) {
        AgentPilotTenant tenant = tenantService.createTenant(request);
        adminAuditService.record("tenant.create", "tenant", tenant.getId(), "Created tenant " + tenant.getName());
        return ApiResponse.ok(tenant);
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> update(
            @PathVariable String tenantId,
            @RequestBody TenantUpsertRequest request
    ) {
        AgentPilotTenant tenant = tenantService.updateTenant(tenantId, request);
        adminAuditService.record("tenant.update", "tenant", tenant.getId(), "Updated tenant " + tenant.getName());
        return ApiResponse.ok(tenant);
    }

    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> changeStatus(
            @PathVariable String tenantId,
            @RequestBody TenantStatusRequest request
    ) {
        AgentPilotTenant tenant = tenantService.changeStatus(tenantId, request == null ? null : request.status());
        adminAuditService.record("tenant.status", "tenant", tenant.getId(), "Changed tenant status to " + tenant.getStatus());
        return ApiResponse.ok(tenant);
    }

    @GetMapping("/{tenantId}/configs")
    public ApiResponse<List<TenantConfig>> configs(@PathVariable String tenantId) {
        return ApiResponse.ok(tenantConfigService.listByTenant(tenantId));
    }

    @GetMapping("/{tenantId}/configs/effective")
    public ApiResponse<List<TenantConfigResolver.ResolvedTenantConfig>> effectiveConfigs(@PathVariable String tenantId) {
        return ApiResponse.ok(tenantConfigResolver.resolveCommon(tenantId));
    }

    @PutMapping("/{tenantId}/configs")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<TenantConfig> upsertConfig(
            @PathVariable String tenantId,
            @RequestBody TenantConfigUpsertRequest request
    ) {
        TenantConfig config = tenantConfigService.upsert(tenantId, request, CurrentUser.userId());
        adminAuditService.record(
                "tenant.config.upsert",
                "tenant_config",
                tenantId + ":" + config.getConfigKey(),
                "Updated tenant config " + config.getConfigKey()
        );
        return ApiResponse.ok(config);
    }

    @DeleteMapping("/{tenantId}/configs/{configKey}")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String tenantId,
            @PathVariable String configKey
    ) {
        tenantConfigService.removeConfig(tenantId, configKey);
        adminAuditService.record(
                "tenant.config.delete",
                "tenant_config",
                tenantId + ":" + configKey,
                "Deleted tenant config " + configKey
        );
        return ApiResponse.ok(null);
    }
}
