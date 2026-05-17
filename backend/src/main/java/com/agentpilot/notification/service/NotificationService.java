package com.agentpilot.notification.service;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.notification.entity.AgentPilotNotification;
import com.agentpilot.notification.mapper.AgentPilotNotificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService extends ServiceImpl<AgentPilotNotificationMapper, AgentPilotNotification> {
    private final NotificationDeliveryService deliveryService;

    public NotificationService(NotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    public void notifyConfirmationRequired(AgentRun run, AgentConfirmation confirmation) {
        if (run == null || confirmation == null || run.getUserId() == null || run.getTenantId() == null) {
            return;
        }
        String dedupeKey = "confirmation:" + confirmation.getId();
        boolean exists = count(new LambdaQueryWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getTenantId, run.getTenantId())
                .eq(AgentPilotNotification::getRecipientUserId, run.getUserId())
                .eq(AgentPilotNotification::getDedupeKey, dedupeKey)) > 0;
        if (exists) {
            return;
        }
        AgentPilotNotification notification = new AgentPilotNotification();
        notification.setTenantId(run.getTenantId());
        notification.setRecipientUserId(run.getUserId());
        notification.setSalesRepId(run.getSalesRepId());
        notification.setType("CONFIRMATION_REQUIRED");
        notification.setTitle("有一条 CRM 写入需要你确认");
        notification.setContent(confirmation.getActionSummary());
        notification.setActionUrl("/?confirmationId=" + confirmation.getId());
        notification.setSourceType("confirmation");
        notification.setSourceId(String.valueOf(confirmation.getId()));
        notification.setStatus("UNREAD");
        notification.setDedupeKey(dedupeKey);
        notification.setCreatedAt(LocalDateTime.now());
        save(notification);
        deliveryService.deliver(notification);
    }

    public List<AgentPilotNotification> recentForUser(String tenantId, Long userId, String status, int limit) {
        LambdaQueryWrapper<AgentPilotNotification> wrapper = new LambdaQueryWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getTenantId, tenantId)
                .eq(AgentPilotNotification::getRecipientUserId, userId)
                .orderByDesc(AgentPilotNotification::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 100)));
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(AgentPilotNotification::getStatus, status.toUpperCase());
        }
        return list(wrapper);
    }

    public long unreadCount(String tenantId, Long userId) {
        return count(new LambdaQueryWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getTenantId, tenantId)
                .eq(AgentPilotNotification::getRecipientUserId, userId)
                .eq(AgentPilotNotification::getStatus, "UNREAD"));
    }

    public boolean markRead(String tenantId, Long userId, Long notificationId) {
        return update(new LambdaUpdateWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getId, notificationId)
                .eq(AgentPilotNotification::getTenantId, tenantId)
                .eq(AgentPilotNotification::getRecipientUserId, userId)
                .set(AgentPilotNotification::getStatus, "READ")
                .set(AgentPilotNotification::getReadAt, LocalDateTime.now()));
    }

    public int markAllRead(String tenantId, Long userId) {
        return getBaseMapper().update(null, new LambdaUpdateWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getTenantId, tenantId)
                .eq(AgentPilotNotification::getRecipientUserId, userId)
                .eq(AgentPilotNotification::getStatus, "UNREAD")
                .set(AgentPilotNotification::getStatus, "READ")
                .set(AgentPilotNotification::getReadAt, LocalDateTime.now()));
    }

    public void markSourceRead(String tenantId, Long userId, String sourceType, String sourceId) {
        update(new LambdaUpdateWrapper<AgentPilotNotification>()
                .eq(AgentPilotNotification::getTenantId, tenantId)
                .eq(AgentPilotNotification::getRecipientUserId, userId)
                .eq(AgentPilotNotification::getSourceType, sourceType)
                .eq(AgentPilotNotification::getSourceId, sourceId)
                .set(AgentPilotNotification::getStatus, "READ")
                .set(AgentPilotNotification::getReadAt, LocalDateTime.now()));
    }
}
