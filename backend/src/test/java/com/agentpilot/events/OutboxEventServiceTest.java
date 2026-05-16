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
}
