package com.agentpilot.tenant.service;

import com.agentpilot.tenant.entity.TenantConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TenantConfigResolver {
    private final TenantConfigService tenantConfigService;

    public record ResolvedTenantConfig(
            String tenantId,
            String configKey,
            String value,
            String valueType,
            String source
    ) {
    }

    public TenantConfigResolver(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    public ResolvedTenantConfig resolve(String tenantId, String key, String globalValue, String valueType) {
        String normalizedTenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : "demo";
        String normalizedKey = requireKey(key);
        TenantConfig override = tenantConfigService.getOne(new LambdaQueryWrapper<TenantConfig>()
                .eq(TenantConfig::getTenantId, normalizedTenantId)
                .eq(TenantConfig::getConfigKey, normalizedKey), false);
        if (override != null && StringUtils.hasText(override.getConfigValue())) {
            return new ResolvedTenantConfig(
                    normalizedTenantId,
                    normalizedKey,
                    override.getConfigValue(),
                    StringUtils.hasText(override.getValueType()) ? override.getValueType() : valueType,
                    "TENANT"
            );
        }
        if (StringUtils.hasText(globalValue)) {
            return new ResolvedTenantConfig(normalizedTenantId, normalizedKey, globalValue.trim(), valueType, "GLOBAL");
        }
        return new ResolvedTenantConfig(normalizedTenantId, normalizedKey, "", valueType, "DEFAULT");
    }

    public boolean resolveBoolean(String tenantId, String key, boolean globalValue) {
        ResolvedTenantConfig resolved = resolve(tenantId, key, String.valueOf(globalValue), "boolean");
        return "true".equalsIgnoreCase(resolved.value()) || "1".equals(resolved.value());
    }

    public List<ResolvedTenantConfig> resolveCommon(String tenantId) {
        return List.of(
                resolve(tenantId, "model.chat.model", "", "string"),
                resolve(tenantId, "model.embedding.model", "", "string"),
                resolve(tenantId, "notification.webhook.url", "", "string"),
                resolve(tenantId, "rateLimit.agentChat.capacity", "", "number"),
                resolve(tenantId, "retention.agentRun.days", "", "number"),
                resolve(tenantId, "callcenter.provider", "", "string"),
                resolve(tenantId, "callcenter.asr.provider", "", "string")
        );
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("configKey is required");
        }
        return key.trim();
    }
}
