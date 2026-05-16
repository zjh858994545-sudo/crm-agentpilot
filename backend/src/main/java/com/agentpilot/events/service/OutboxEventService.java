package com.agentpilot.events.service;

import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.mapper.OutboxEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventService extends ServiceImpl<OutboxEventMapper, OutboxEvent> {
    public static final int MAX_RETRY_COUNT = 5;
    public static final Duration DISPATCH_LOCK_TTL = Duration.ofMinutes(2);

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

    public long dispatchingCount() {
        return count(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "DISPATCHING"));
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
        update(new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, event.getId())
                .set(OutboxEvent::getStatus, "PUBLISHED")
                .set(OutboxEvent::getPublishedAt, LocalDateTime.now())
                .set(OutboxEvent::getErrorMessage, null)
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedAt, null));
    }

    public void markFailed(OutboxEvent event, String message) {
        int nextRetryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        update(new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, event.getId())
                .set(OutboxEvent::getStatus, nextRetryCount >= MAX_RETRY_COUNT ? "DEAD_LETTER" : "FAILED")
                .set(OutboxEvent::getRetryCount, nextRetryCount)
                .set(OutboxEvent::getErrorMessage, message == null ? "unknown error" : message)
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedAt, null));
    }

    public boolean claimForDispatch(Long id, String workerId) {
        return update(new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getId, id)
                .in(OutboxEvent::getStatus, List.of("PENDING", "FAILED"))
                .lt(OutboxEvent::getRetryCount, MAX_RETRY_COUNT)
                .set(OutboxEvent::getStatus, "DISPATCHING")
                .set(OutboxEvent::getLockedBy, workerId)
                .set(OutboxEvent::getLockedAt, LocalDateTime.now()));
    }

    public int releaseStaleDispatching() {
        LocalDateTime threshold = LocalDateTime.now().minus(DISPATCH_LOCK_TTL);
        return getBaseMapper().update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "DISPATCHING")
                .lt(OutboxEvent::getLockedAt, threshold)
                .set(OutboxEvent::getStatus, "FAILED")
                .set(OutboxEvent::getErrorMessage, "dispatch lock expired")
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedAt, null));
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
                .set(OutboxEvent::getPublishedAt, null)
                .set(OutboxEvent::getLockedBy, null)
                .set(OutboxEvent::getLockedAt, null));
    }
}
