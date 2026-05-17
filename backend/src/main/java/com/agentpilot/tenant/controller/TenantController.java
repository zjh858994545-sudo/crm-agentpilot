package com.agentpilot.tenant.controller;

import com.agentpilot.common.response.ApiResponse;
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

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ApiResponse<List<AgentPilotTenant>> list() {
        return ApiResponse.ok(tenantService.listTenants());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> create(@RequestBody TenantUpsertRequest request) {
        return ApiResponse.ok(tenantService.createTenant(request));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> update(
            @PathVariable String tenantId,
            @RequestBody TenantUpsertRequest request
    ) {
        return ApiResponse.ok(tenantService.updateTenant(tenantId, request));
    }

    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<AgentPilotTenant> changeStatus(
            @PathVariable String tenantId,
            @RequestBody TenantStatusRequest request
    ) {
        return ApiResponse.ok(tenantService.changeStatus(tenantId, request == null ? null : request.status()));
    }
}
