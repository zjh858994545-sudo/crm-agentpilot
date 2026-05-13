package com.agentpilot.events;

import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.events.config.EventProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentPilotEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(AgentPilotEventPublisher.class);

    private final EventProperties properties;
    private final ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public AgentPilotEventPublisher(
            EventProperties properties,
            ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
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
        if (!properties.isKafkaEnabled()) {
            log.info("event topic={} type={} aggregateId={}", topic, eventType, aggregateId);
            return;
        }
        KafkaTemplate<Object, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Kafka event publishing is enabled but KafkaTemplate is unavailable. topic={} type={}", topic, eventType);
            return;
        }
        try {
            kafkaTemplate.send(topic, event.aggregateId(), objectMapper.writeValueAsString(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka event publish failed. topic={} type={} message={}", topic, eventType, ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException ex) {
            log.warn("Kafka event serialization failed. topic={} type={} message={}", topic, eventType, ex.getMessage());
        }
    }
}
