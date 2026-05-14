package com.agentpilot.agent.controller;

import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.orchestrator.AgentOrchestrator;
import com.agentpilot.agent.service.AgentRunService;
import com.agentpilot.agent.service.AgentToolCallService;
import com.agentpilot.agent.tool.AgentToolDefinition;
import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.agent.vo.AgentChatRequest;
import com.agentpilot.agent.vo.AgentChatResponse;
import com.agentpilot.agent.vo.ConfirmationDecisionRequest;
import com.agentpilot.common.response.ApiResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentOrchestrator orchestrator;
    private final AgentRunService runService;
    private final AgentToolCallService toolCallService;
    private final ToolRegistry toolRegistry;

    public AgentController(
            AgentOrchestrator orchestrator,
            AgentRunService runService,
            AgentToolCallService toolCallService,
            ToolRegistry toolRegistry
    ) {
        this.orchestrator = orchestrator;
        this.runService = runService;
        this.toolCallService = toolCallService;
        this.toolRegistry = toolRegistry;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        return ApiResponse.ok(orchestrator.chat(request));
    }

    @PostMapping("/confirmations/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmationDecisionRequest request
    ) {
        return ApiResponse.ok(orchestrator.confirm(id, request.userId()));
    }

    @PostMapping("/confirmations/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmationDecisionRequest request
    ) {
        return ApiResponse.ok(orchestrator.reject(id, request.userId()));
    }

    @GetMapping("/tools")
    public ApiResponse<List<AgentToolDefinition>> tools() {
        return ApiResponse.ok(toolRegistry.list());
    }

    @GetMapping("/runs")
    public ApiResponse<List<AgentRun>> runs() {
        return ApiResponse.ok(runService.list(new LambdaQueryWrapper<AgentRun>().orderByDesc(AgentRun::getId)));
    }

    @GetMapping("/runs/{runId}/tool-calls")
    public ApiResponse<List<AgentToolCall>> toolCalls(@PathVariable Long runId) {
        return ApiResponse.ok(toolCallService.listByRunId(runId));
    }
}
