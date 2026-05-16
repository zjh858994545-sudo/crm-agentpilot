package com.agentpilot.agent.vo;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;

import java.util.List;

public record AgentExecutionTrace(
        AgentRun run,
        List<AgentToolCall> toolCalls,
        List<AgentConfirmation> confirmations,
        List<AgentExecutionStep> steps,
        String routingMode,
        String currentStage,
        String safetyBoundary,
        boolean requiresConfirmation
) {
}
