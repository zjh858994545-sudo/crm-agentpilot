package com.agentpilot.export.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.export.entity.ExportRequest;
import com.agentpilot.export.service.ExportRequestService;
import com.agentpilot.export.vo.ExportDecisionRequest;
import com.agentpilot.export.vo.ExportRequestCreateRequest;
import com.agentpilot.operations.service.AdminAuditService;
import com.agentpilot.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/export-requests")
@PreAuthorize("hasAuthority('export:request')")
public class ExportRequestController {
    private final ExportRequestService exportRequestService;
    private final AdminAuditService adminAuditService;

    public ExportRequestController(
            ExportRequestService exportRequestService,
            AdminAuditService adminAuditService
    ) {
        this.exportRequestService = exportRequestService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public ApiResponse<List<ExportRequest>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(exportRequestService.listByTenant(CurrentUser.tenantId(), status, limit));
    }

    @PostMapping
    public ApiResponse<ExportRequest> create(@RequestBody ExportRequestCreateRequest request) {
        ExportRequest exportRequest = exportRequestService.create(CurrentUser.tenantId(), CurrentUser.userId(), request);
        adminAuditService.record(
                "export.request",
                "export_request",
                String.valueOf(exportRequest.getId()),
                "Requested export " + exportRequest.getExportType()
        );
        return ApiResponse.ok(exportRequest);
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('export:approve')")
    public ApiResponse<ExportRequest> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ExportDecisionRequest request
    ) {
        ExportRequest exportRequest = exportRequestService.approve(
                id,
                CurrentUser.tenantId(),
                CurrentUser.userId(),
                request == null ? "" : request.comment()
        );
        adminAuditService.record(
                "export.approve",
                "export_request",
                String.valueOf(exportRequest.getId()),
                "Approved export " + exportRequest.getExportType()
        );
        return ApiResponse.ok(exportRequest);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('export:approve')")
    public ApiResponse<ExportRequest> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ExportDecisionRequest request
    ) {
        ExportRequest exportRequest = exportRequestService.reject(
                id,
                CurrentUser.tenantId(),
                CurrentUser.userId(),
                request == null ? "" : request.comment()
        );
        adminAuditService.record(
                "export.reject",
                "export_request",
                String.valueOf(exportRequest.getId()),
                "Rejected export " + exportRequest.getExportType()
        );
        return ApiResponse.ok(exportRequest);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ExportRequestService.ExportDownload download = exportRequestService.download(
                id,
                CurrentUser.tenantId(),
                CurrentUser.userId(),
                CurrentUser.require().permissions().contains("export:approve")
        );
        adminAuditService.record(
                "export.download",
                "export_request",
                String.valueOf(id),
                "Downloaded export file " + download.fileName()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .body(download.content());
    }
}
