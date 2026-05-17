package com.agentpilot.tenant.vo;

public record TenantUpsertRequest(
        String id,
        String name,
        String planCode
) {
}
