package com.agentpilot.tenant.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.operations.service.AdminAuditService;
import com.agentpilot.tenant.entity.AgentPilotTenant;
import com.agentpilot.tenant.service.TenantService;
import com.agentpilot.tenant.vo.TenantStatusRequest;
import com.agentpilot.tenant.vo.TenantUpsertRequest;
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
    private final AdminAuditService adminAuditService;

    public TenantController(TenantService tenantService, AdminAuditService adminAuditService) {
        this.tenantService = tenantService;
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
}
