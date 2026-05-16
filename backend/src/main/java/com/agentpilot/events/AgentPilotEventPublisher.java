package com.agentpilot.events;

import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.service.OutboxEventService;
import com.agentpilot.events.config.EventProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AgentPilotEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(AgentPilotEventPublisher.class);

    private final EventProperties properties;
    private final ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private final OutboxEventService outboxEventService;

    public AgentPilotEventPublisher(
            EventProperties properties,
            ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            OutboxEventService outboxEventService
    ) {
        this.properties = properties;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
        this.outboxEventService = outboxEventService;
    }

    public void publishAgentRunCompleted(AgentRun run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("sessionId", run.getSessionId());
        payload.put("intent", run.getIntent());
        payload.put("status", run.getStatus());
        payload.put("modelName", run.getModelName());
        payload.put("latencyMs", run.getLatencyMs());
        publish(properties.getAgentRunTopic(), "agent_run.completed", "agent_run", run.getId(), payload);
    }

    public void publishToolCallRecorded(AgentToolCall call) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", call.getId());
        payload.put("runId", call.getRunId());
        payload.put("toolName", call.getToolName());
        payload.put("toolType", call.getToolType());
        payload.put("status", call.getStatus());
        payload.put("requiresConfirmation", call.getRequiresConfirmation());
        payload.put("latencyMs", call.getLatencyMs());
        publish(properties.getAgentToolCallTopic(), "agent_tool_call.recorded", "agent_tool_call", call.getId(), payload);
    }

    public void publishCrmTaskCreated(CrmTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("customerId", task.getCustomerId());
        payload.put("leadId", task.getLeadId());
        payload.put("salesRepId", task.getSalesRepId());
        payload.put("source", task.getSource());
        payload.put("idempotencyKey", task.getIdempotencyKey());
        publish(properties.getCrmTaskTopic(), "crm_task.created", "crm_task", task.getId(), payload);
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", properties.isKafkaEnabled() ? "kafka" : "log-only");
        result.put("kafkaEnabled", properties.isKafkaEnabled());
        result.put("agentRunTopic", properties.getAgentRunTopic());
        result.put("agentToolCallTopic", properties.getAgentToolCallTopic());
        result.put("crmTaskTopic", properties.getCrmTaskTopic());
        result.put("outboxPending", outboxEventService.pendingCount());
        result.put("outboxDeadLetters", outboxEventService.deadLetterCount());
        result.put("maxRetryCount", OutboxEventService.MAX_RETRY_COUNT);
        return result;
    }

    private void publish(String topic, String eventType, String aggregateType, Long aggregateId, Map<String, Object> payload) {
        AgentPilotEvent event = new AgentPilotEvent(
                UUID.randomUUID().toString(),
                eventType,
                aggregateType,
                aggregateId == null ? "" : aggregateId.toString(),
                MDC.get("traceId"),
                Instant.now(),
                payload
        );
        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventId(event.eventId());
        outbox.setTopic(topic);
        outbox.setEventType(eventType);
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(event.aggregateId());
        outbox.setTraceId(event.traceId());
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        outbox.setCreatedAt(LocalDateTime.now());
        try {
            outbox.setPayloadJson(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            log.warn("Outbox event serialization failed. topic={} type={} message={}", topic, eventType, ex.getMessage());
            return;
        }
        outboxEventService.save(outbox);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchOne(outbox.getId());
                }
            });
        } else {
            dispatchOne(outbox.getId());
        }
    }

    @Scheduled(fixedDelayString = "${agentpilot.events.outbox-dispatch-delay-ms:5000}")
    public void dispatchPending() {
        outboxEventService.listDispatchable(20).forEach(event -> dispatchOne(event.getId()));
    }

    public boolean retryDeadLetter(Long outboxId) {
        if (!outboxEventService.reviveDeadLetter(outboxId)) {
            return false;
        }
        dispatchOne(outboxId);
        return true;
    }

    private void dispatchOne(Long outboxId) {
        OutboxEvent outbox = outboxEventService.getById(outboxId);
        if (outbox == null || "PUBLISHED".equals(outbox.getStatus()) || "DEAD_LETTER".equals(outbox.getStatus())) {
            return;
        }
        if (!properties.isKafkaEnabled()) {
            log.info("outbox event topic={} type={} aggregateId={} mode=log-only",
                    outbox.getTopic(), outbox.getEventType(), outbox.getAggregateId());
            outboxEventService.markPublished(outbox);
            return;
        }
        KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            outboxEventService.markFailed(outbox, "KafkaTemplate is unavailable");
            return;
        }
        try {
            kafkaTemplate.send(outbox.getTopic(), outbox.getAggregateId(), outbox.getPayloadJson())
                    .get(5, TimeUnit.SECONDS);
            outboxEventService.markPublished(outbox);
        } catch (Exception ex) {
            log.warn("Kafka outbox dispatch failed. topic={} type={} message={}",
                    outbox.getTopic(), outbox.getEventType(), ex.getMessage());
            outboxEventService.markFailed(outbox, ex.getMessage());
        }
    }
}
