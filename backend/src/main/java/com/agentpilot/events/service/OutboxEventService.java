package com.agentpilot.events.service;

import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.mapper.OutboxEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventService extends ServiceImpl<OutboxEventMapper, OutboxEvent> {
    public static final int MAX_RETRY_COUNT = 5;

    public List<OutboxEvent> listDispatchable(int limit) {
        return list(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, List.of("PENDING", "FAILED"))
                .lt(OutboxEvent::getRetryCount, MAX_RETRY_COUNT)
                .orderByAsc(OutboxEvent::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    public long pendingCount() {
        return count(new LambdaQueryWrapper<OutboxEvent>()
                .in(OutboxEvent::getStatus, List.of("PENDING", "FAILED")));
    }

    public long deadLetterCount() {
        return count(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "DEAD_LETTER"));
    }

    public List<OutboxEvent> recentDeadLetters(int limit) {
        return list(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "DEAD_LETTER")
                .orderByDesc(OutboxEvent::getCreatedAt)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    public void markPublished(OutboxEvent event) {
        event.setStatus("PUBLISHED");
        event.setPublishedAt(LocalDateTime.now());
        event.setErrorMessage(null);
        updateById(event);
    }

    public void markFailed(OutboxEvent event, String message) {
        int nextRetryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        event.setStatus(nextRetryCount >= MAX_RETRY_COUNT ? "DEAD_LETTER" : "FAILED");
        event.setRetryCount(nextRetryCount);
        event.setErrorMessage(message == null ? "unknown error" : message);
        updateById(event);
    }

    public boolean reviveDeadLetter(Long id) {
        OutboxEvent event = getById(id);
        if (event == null || !"DEAD_LETTER".equals(event.getStatus())) {
            return false;
        }
        return update(new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, id)
                .eq(OutboxEvent::getStatus, "DEAD_LETTER")
                .set(OutboxEvent::getStatus, "PENDING")
                .set(OutboxEvent::getRetryCount, 0)
                .set(OutboxEvent::getErrorMessage, null)
                .set(OutboxEvent::getPublishedAt, null));
    }
}
