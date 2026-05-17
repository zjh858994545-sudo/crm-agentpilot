package com.agentpilot.dashboard.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.dashboard.service.DashboardMetricsService;
import com.agentpilot.dashboard.vo.DashboardMetrics;
import com.agentpilot.dashboard.vo.DashboardTeamMemberMetric;
import com.agentpilot.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAuthority('crm:read')")
public class DashboardController {
    private final DashboardMetricsService metricsService;

    public DashboardController(DashboardMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public ApiResponse<DashboardMetrics> metrics() {
        return ApiResponse.ok(metricsService.metrics(CurrentUser.tenantId(), CurrentUser.salesRepId(), CurrentUser.userId()));
    }

    @GetMapping("/team-metrics")
    public ApiResponse<List<DashboardTeamMemberMetric>> teamMetrics() {
        if (!CurrentUser.isManagerOrAdmin()) {
            throw new AccessDeniedException("team dashboard requires manager or admin role");
        }
        return ApiResponse.ok(metricsService.teamMetrics(CurrentUser.tenantId()));
    }
}
