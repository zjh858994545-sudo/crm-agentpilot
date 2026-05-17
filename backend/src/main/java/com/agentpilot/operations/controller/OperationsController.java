package com.agentpilot.operations.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.operations.service.AdminAuditService;
import com.agentpilot.operations.service.LaunchReadinessService;
import com.agentpilot.operations.service.OperationsDiagnosticService;
import com.agentpilot.operations.service.RetentionMaintenanceService;
import com.agentpilot.operations.service.UsageMetricsService;
import com.agentpilot.operations.vo.LaunchReadinessStatus;
import com.agentpilot.operations.vo.RetentionCleanupRequest;
import com.agentpilot.operations.vo.RetentionCleanupResult;
import com.agentpilot.operations.vo.RetentionStatus;
import com.agentpilot.operations.vo.UsageSnapshot;
import com.agentpilot.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operations")
@PreAuthorize("hasAuthority('ops:read')")
public class OperationsController {
    private final AdminAuditService adminAuditService;
    private final RetentionMaintenanceService retentionMaintenanceService;
    private final LaunchReadinessService launchReadinessService;
    private final UsageMetricsService usageMetricsService;
    private final OperationsDiagnosticService operationsDiagnosticService;

    public OperationsController(
            AdminAuditService adminAuditService,
            RetentionMaintenanceService retentionMaintenanceService,
            LaunchReadinessService launchReadinessService,
            UsageMetricsService usageMetricsService,
            OperationsDiagnosticService operationsDiagnosticService
    ) {
        this.adminAuditService = adminAuditService;
        this.retentionMaintenanceService = retentionMaintenanceService;
        this.launchReadinessService = launchReadinessService;
        this.usageMetricsService = usageMetricsService;
        this.operationsDiagnosticService = operationsDiagnosticService;
    }

    @GetMapping("/readiness")
    public ApiResponse<LaunchReadinessStatus> readiness() {
        return ApiResponse.ok(launchReadinessService.status());
    }

    @GetMapping("/retention")
    public ApiResponse<RetentionStatus> retentionStatus() {
        return ApiResponse.ok(retentionMaintenanceService.status());
    }

    @GetMapping("/usage")
    public ApiResponse<UsageSnapshot> usage() {
        return ApiResponse.ok(usageMetricsService.snapshot(CurrentUser.tenantId()));
    }

    @GetMapping("/diagnostics.zip")
    public ResponseEntity<byte[]> diagnosticsBundle() {
        byte[] payload = operationsDiagnosticService.buildBundle(CurrentUser.tenantId(), CurrentUser.userId());
        adminAuditService.record(
                "operations.diagnostics.download",
                "diagnostics",
                CurrentUser.tenantId(),
                "Downloaded operations diagnostic bundle"
        );
        String filename = "agentpilot-diagnostics-" + CurrentUser.tenantId() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(payload);
    }

    @PostMapping("/retention/run")
    @PreAuthorize("hasAuthority('ops:write')")
    public ApiResponse<RetentionCleanupResult> runRetention(@RequestBody(required = false) RetentionCleanupRequest request) {
        boolean dryRun = request == null || request.dryRunValue();
        RetentionCleanupResult result = retentionMaintenanceService.cleanup(dryRun);
        adminAuditService.record(
                dryRun ? "operations.retention.dry-run" : "operations.retention.cleanup",
                "retention",
                "global",
                "Retention " + (dryRun ? "dry-run" : "cleanup") + ": eligible=" + result.totalEligibleRows() + ", deleted=" + result.totalDeletedRows()
        );
        return ApiResponse.ok(result);
    }

    @GetMapping("/admin-audit")
    public ApiResponse<List<AdminAuditService.AdminAuditLog>> adminAudit() {
        return ApiResponse.ok(adminAuditService.recent(CurrentUser.tenantId(), 100));
    }
}
