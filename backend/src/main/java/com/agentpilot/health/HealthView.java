package com.agentpilot.health;

import java.time.OffsetDateTime;
import java.util.Map;

public record HealthView(
        String status,
        String app,
        String version,
        String phase,
        String modelProvider,
        OffsetDateTime checkedAt,
        Map<String, String> modules
) {
}

