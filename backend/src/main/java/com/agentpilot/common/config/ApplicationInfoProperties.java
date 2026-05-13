package com.agentpilot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.app")
public record ApplicationInfoProperties(
        String name,
        String version,
        String phase,
        String modelProvider
) {
}

