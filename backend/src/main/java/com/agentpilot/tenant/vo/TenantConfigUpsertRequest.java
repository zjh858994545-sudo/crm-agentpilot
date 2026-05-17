package com.agentpilot.tenant.vo;

public record TenantConfigUpsertRequest(
        String configKey,
        String configValue,
        String valueType,
        String description
) {
}
