package com.agentpilot.operations.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.operations.service.RetentionMaintenanceService;
import com.agentpilot.operations.vo.RetentionCleanupRequest;
import com.agentpilot.operations.vo.RetentionCleanupResult;
import com.agentpilot.operations.vo.RetentionStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
@PreAuthorize("hasAuthority('ops:read')")
public class OperationsController {
    private final RetentionMaintenanceService retentionMaintenanceService;

    public OperationsController(RetentionMaintenanceService retentionMaintenanceService) {
        this.retentionMaintenanceService = retentionMaintenanceService;
    }

    @GetMapping("/retention")
    public ApiResponse<RetentionStatus> retentionStatus() {
        return ApiResponse.ok(retentionMaintenanceService.status());
    }

    @PostMapping("/retention/run")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<RetentionCleanupResult> runRetention(@RequestBody(required = false) RetentionCleanupRequest request) {
        boolean dryRun = request == null || request.dryRunValue();
        return ApiResponse.ok(retentionMaintenanceService.cleanup(dryRun));
    }
}
