package com.agentpilot.tenant.service;

import com.agentpilot.tenant.entity.TenantConfig;
import com.agentpilot.tenant.mapper.TenantConfigMapper;
import com.agentpilot.tenant.vo.TenantConfigUpsertRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TenantConfigService extends ServiceImpl<TenantConfigMapper, TenantConfig> {
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]{1,127}$");
    private static final Set<String> ALLOWED_VALUE_TYPES = Set.of("string", "number", "boolean", "json");

    public List<TenantConfig> listByTenant(String tenantId) {
        return list(new LambdaQueryWrapper<TenantConfig>()
                .eq(TenantConfig::getTenantId, requireText(tenantId, "tenantId"))
                .orderByAsc(TenantConfig::getConfigKey));
    }

    @Transactional
    public TenantConfig upsert(String tenantId, TenantConfigUpsertRequest request, Long actorUserId) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String key = normalizeKey(request == null ? null : request.configKey());
        String valueType = normalizeValueType(request == null ? null : request.valueType());
        String value = request == null ? "" : defaultText(request.configValue(), "");
        String description = request == null ? "" : defaultText(request.description(), "");
        boolean updated = update(new LambdaUpdateWrapper<TenantConfig>()
                .eq(TenantConfig::getTenantId, normalizedTenantId)
                .eq(TenantConfig::getConfigKey, key)
                .set(TenantConfig::getConfigValue, value)
                .set(TenantConfig::getValueType, valueType)
                .set(TenantConfig::getDescription, description)
                .set(TenantConfig::getUpdatedBy, actorUserId)
                .set(TenantConfig::getUpdatedAt, LocalDateTime.now()));
        if (!updated) {
            TenantConfig config = new TenantConfig();
            config.setTenantId(normalizedTenantId);
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setValueType(valueType);
            config.setDescription(description);
            config.setUpdatedBy(actorUserId);
            config.setUpdatedAt(LocalDateTime.now());
            save(config);
        }
        return getOne(new LambdaQueryWrapper<TenantConfig>()
                .eq(TenantConfig::getTenantId, normalizedTenantId)
                .eq(TenantConfig::getConfigKey, key), false);
    }

    @Transactional
    public boolean removeConfig(String tenantId, String configKey) {
        return remove(new LambdaQueryWrapper<TenantConfig>()
                .eq(TenantConfig::getTenantId, requireText(tenantId, "tenantId"))
                .eq(TenantConfig::getConfigKey, normalizeKey(configKey)));
    }

    private String normalizeKey(String configKey) {
        String key = requireText(configKey, "configKey");
        if (!CONFIG_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("configKey must be 2-128 chars and use letters, numbers, '.', '_' or '-'");
        }
        return key;
    }

    private String normalizeValueType(String valueType) {
        String normalized = defaultText(valueType, "string").toLowerCase(Locale.ROOT);
        if (!ALLOWED_VALUE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("valueType must be string, number, boolean or json");
        }
        return normalized;
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
