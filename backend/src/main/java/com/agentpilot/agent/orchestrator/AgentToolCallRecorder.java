package com.agentpilot.agent.orchestrator;

import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.service.AgentToolCallService;
import com.agentpilot.agent.tool.AgentToolDefinition;
import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.agent.tool.ToolType;
import com.agentpilot.agent.vo.ToolCallView;
import com.agentpilot.events.AgentPilotEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AgentToolCallRecorder {
    private final AgentToolCallService toolCallService;
    private final ToolRegistry toolRegistry;
    private final AgentPilotEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AgentToolCallRecorder(
            AgentToolCallService toolCallService,
            ToolRegistry toolRegistry,
            AgentPilotEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.toolCallService = toolCallService;
        this.toolRegistry = toolRegistry;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public AgentToolCall record(Long runId, String toolName, Object input, Object output, String status, String errorMessage, long latencyMs) {
        AgentToolDefinition definition = toolRegistry.find(toolName)
                .orElse(new AgentToolDefinition(toolName, "unknown", ToolType.READ, false, List.of(), Map.of()));
        AgentToolCall call = new AgentToolCall();
        call.setRunId(runId);
        call.setToolName(toolName);
        call.setToolType(definition.type().name());
        call.setRequiresConfirmation(definition.requiresConfirmation());
        call.setInputJson(toJson(input));
        call.setOutputJson(toJson(output));
        call.setStatus(status);
        call.setLatencyMs(Math.max(0L, latencyMs));
        call.setErrorMessage(errorMessage);
        call.setCompletedAt(LocalDateTime.now());
        toolCallService.save(call);
        eventPublisher.publishToolCallRecorded(call);
        return call;
    }

    public ToolCallView view(AgentToolCall call) {
        return new ToolCallView(
                call.getId(),
                call.getToolName(),
                call.getToolType(),
                call.getStatus(),
                Boolean.TRUE.equals(call.getRequiresConfirmation()),
                call.getConfirmationId()
        );
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
