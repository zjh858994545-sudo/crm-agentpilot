package com.agentpilot.events.service;

import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.mapper.OutboxEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventService extends ServiceImpl<OutboxEventMapper, OutboxEvent> {

    public List<OutboxEvent> listDispatchable(int limit) {
        return list(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, List.of("PENDING", "FAILED"))
                .lt(OutboxEvent::getRetryCount, 5)
                .orderByAsc(OutboxEvent::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    public long pendingCount() {
        return count(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, List.of("PENDING", "FAILED")));
    }

    public void markPublished(OutboxEvent event) {
        event.setStatus("PUBLISHED");
        event.setPublishedAt(LocalDateTime.now());
        event.setErrorMessage(null);
        updateById(event);
    }

    public void markFailed(OutboxEvent event, String message) {
        event.setStatus("FAILED");
        event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
        event.setErrorMessage(message == null ? "unknown error" : message);
        updateById(event);
    }
}
