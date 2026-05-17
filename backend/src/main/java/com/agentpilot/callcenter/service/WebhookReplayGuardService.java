package com.agentpilot.callcenter.service;

import com.agentpilot.callcenter.entity.WebhookReplayGuard;
import com.agentpilot.callcenter.mapper.WebhookReplayGuardMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WebhookReplayGuardService extends ServiceImpl<WebhookReplayGuardMapper, WebhookReplayGuard> {
    public boolean remember(String tenantId, String endpoint, String nonce, String signatureHash, long ttlSeconds) {
        cleanupExpired();

        LocalDateTime now = LocalDateTime.now();
        WebhookReplayGuard guard = new WebhookReplayGuard();
        guard.setTenantId(tenantId);
        guard.setEndpoint(endpoint);
        guard.setNonce(nonce);
        guard.setSignatureHash(signatureHash);
        guard.setReceivedAt(now);
        guard.setExpiresAt(now.plusSeconds(Math.max(60, ttlSeconds)));
        try {
            return save(guard);
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    public void cleanupExpired() {
        remove(new LambdaQueryWrapper<WebhookReplayGuard>()
                .lt(WebhookReplayGuard::getExpiresAt, LocalDateTime.now()));
    }
}
