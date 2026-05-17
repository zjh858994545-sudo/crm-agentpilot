package com.agentpilot.notification.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.notification.entity.AgentPilotNotification;
import com.agentpilot.notification.service.NotificationService;
import com.agentpilot.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAuthority('agent:use')")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<AgentPilotNotification>> list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(notificationService.recentForUser(
                CurrentUser.tenantId(),
                CurrentUser.userId(),
                status,
                limit
        ));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of(
                "unreadCount",
                notificationService.unreadCount(CurrentUser.tenantId(), CurrentUser.userId())
        ));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(@PathVariable Long id) {
        boolean updated = notificationService.markRead(CurrentUser.tenantId(), CurrentUser.userId(), id);
        return ApiResponse.ok(Map.of("updated", updated, "notificationId", id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        int updated = notificationService.markAllRead(CurrentUser.tenantId(), CurrentUser.userId());
        return ApiResponse.ok(Map.of("updated", updated));
    }
}
