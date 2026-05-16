package com.agentpilot.events;

import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.service.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventServiceTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Test
    void failedEventMovesToDeadLetterAndCanBeRevived() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTopic("test-topic");
        event.setEventType("test.event");
        event.setAggregateType("test");
        event.setAggregateId("1");
        event.setPayloadJson("{\"ok\":true}");
        event.setStatus("FAILED");
        event.setRetryCount(OutboxEventService.MAX_RETRY_COUNT - 1);
        event.setErrorMessage("previous failure");
        event.setCreatedAt(LocalDateTime.now());
        outboxEventService.save(event);

        outboxEventService.markFailed(event, "final failure");

        OutboxEvent deadLetter = outboxEventService.getById(event.getId());
        assertThat(deadLetter.getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(deadLetter.getRetryCount()).isEqualTo(OutboxEventService.MAX_RETRY_COUNT);
        assertThat(outboxEventService.recentDeadLetters(10))
                .extracting(OutboxEvent::getId)
                .contains(event.getId());

        assertThat(outboxEventService.reviveDeadLetter(event.getId())).isTrue();

        OutboxEvent revived = outboxEventService.getById(event.getId());
        assertThat(revived.getStatus()).isEqualTo("PENDING");
        assertThat(revived.getRetryCount()).isZero();
        assertThat(revived.getErrorMessage()).isNull();
    }

    @Test
    void dispatchClaimIsSingleWinnerAndStaleLocksAreReleased() {
        OutboxEvent event = newEvent("PENDING", 0);
        outboxEventService.save(event);

        assertThat(outboxEventService.claimForDispatch(event.getId(), "worker-a")).isTrue();
        assertThat(outboxEventService.claimForDispatch(event.getId(), "worker-b")).isFalse();

        OutboxEvent claimed = outboxEventService.getById(event.getId());
        assertThat(claimed.getStatus()).isEqualTo("DISPATCHING");
        assertThat(claimed.getLockedBy()).isEqualTo("worker-a");
        assertThat(claimed.getLockedAt()).isNotNull();

        claimed.setLockedAt(LocalDateTime.now().minus(OutboxEventService.DISPATCH_LOCK_TTL).minusSeconds(1));
        outboxEventService.updateById(claimed);

        assertThat(outboxEventService.releaseStaleDispatching()).isGreaterThanOrEqualTo(1);
        OutboxEvent released = outboxEventService.getById(event.getId());
        assertThat(released.getStatus()).isEqualTo("FAILED");
        assertThat(released.getLockedBy()).isNull();
        assertThat(released.getLockedAt()).isNull();
    }

    private OutboxEvent newEvent(String status, int retryCount) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTopic("test-topic");
        event.setEventType("test.event");
        event.setAggregateType("test");
        event.setAggregateId("1");
        event.setPayloadJson("{\"ok\":true}");
        event.setStatus(status);
        event.setRetryCount(retryCount);
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
