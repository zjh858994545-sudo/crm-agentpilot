package com.agentpilot.health;

import com.agentpilot.common.config.ApplicationInfoProperties;
import com.agentpilot.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ApplicationInfoProperties properties;

    public HealthController(ApplicationInfoProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<HealthView> health() {
        return ApiResponse.ok(new HealthView(
                "UP",
                properties.name(),
                properties.version(),
                properties.phase(),
                properties.modelProvider(),
                OffsetDateTime.now(),
                Map.of(
                        "crm", "ready",
                        "rag", "ready",
                        "agent", "ready",
                        "callcenter", "ready",
                        "evaluation", "ready"
                )
        ));
    }
}
