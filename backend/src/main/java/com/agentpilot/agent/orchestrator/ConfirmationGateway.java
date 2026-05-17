package com.agentpilot.agent.orchestrator;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.service.AgentConfirmationService;
import com.agentpilot.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ConfirmationGateway {
    private final AgentConfirmationService confirmationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ConfirmationGateway(
            AgentConfirmationService confirmationService,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.confirmationService = confirmationService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public AgentConfirmation createPending(
            AgentRun run,
            AgentToolCall call,
            String actionType,
            String actionSummary,
            Map<String, Object> payload
    ) {
        AgentConfirmation confirmation = new AgentConfirmation();
        confirmation.setRunId(run.getId());
        confirmation.setToolCallId(call.getId());
        confirmation.setActionType(actionType);
        confirmation.setActionSummary(actionSummary);
        confirmation.setPayloadJson(toJson(payload));
        confirmation.setStatus("PENDING");
        confirmation.setExpiredAt(LocalDateTime.now().plusHours(24));
        confirmationService.save(confirmation);
        notificationService.notifyConfirmationRequired(run, confirmation);
        return confirmation;
    }

    public void markNotificationRead(String tenantId, Long userId, Long confirmationId) {
        notificationService.markSourceRead(tenantId, userId, "confirmation", String.valueOf(confirmationId));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
