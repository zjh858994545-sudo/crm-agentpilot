package com.agentpilot.events;

import java.time.Instant;
import java.util.Map;

public record AgentPilotEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String traceId,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
