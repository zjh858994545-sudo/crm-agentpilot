package com.agentpilot.events;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.service.OutboxEventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@PreAuthorize("hasAuthority('events:read')")
public class EventController {
    private final AgentPilotEventPublisher eventPublisher;
    private final OutboxEventService outboxEventService;

    public EventController(
            AgentPilotEventPublisher eventPublisher,
            OutboxEventService outboxEventService
    ) {
        this.eventPublisher = eventPublisher;
        this.outboxEventService = outboxEventService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(eventPublisher.status());
    }

    @GetMapping("/dead-letters")
    public ApiResponse<List<OutboxEvent>> deadLetters() {
        return ApiResponse.ok(outboxEventService.recentDeadLetters(50));
    }

    @PostMapping("/dead-letters/{id}/retry")
    @PreAuthorize("hasAuthority('events:write')")
    public ApiResponse<Map<String, Object>> retryDeadLetter(@PathVariable Long id) {
        boolean accepted = eventPublisher.retryDeadLetter(id);
        return ApiResponse.ok(Map.of("accepted", accepted, "outboxId", id));
    }
}
