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
import com.agentpilot.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasAuthority('agent:use')")
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
        Long currentUserId = CurrentUser.userId();
        if (!Objects.equals(request.userId(), currentUserId)) {
            throw new AccessDeniedException("request.userId does not match authenticated user");
        }
        AgentChatRequest securedRequest = new AgentChatRequest(
                request.sessionId(),
                currentUserId,
                request.salesRepId() == null ? CurrentUser.salesRepId() : request.salesRepId(),
                request.customerId(),
                request.message()
        );
        return ApiResponse.ok(orchestrator.chat(securedRequest));
    }

    @PostMapping("/confirmations/{id}/confirm")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmationDecisionRequest request
    ) {
        Long currentUserId = CurrentUser.userId();
        if (!request.userId().equals(currentUserId)) {
            throw new AccessDeniedException("request.userId does not match authenticated user");
        }
        return ApiResponse.ok(orchestrator.confirm(id, currentUserId));
    }

    @PostMapping("/confirmations/{id}/reject")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmationDecisionRequest request
    ) {
        Long currentUserId = CurrentUser.userId();
        if (!request.userId().equals(currentUserId)) {
            throw new AccessDeniedException("request.userId does not match authenticated user");
        }
        return ApiResponse.ok(orchestrator.reject(id, currentUserId));
    }

    @GetMapping("/tools")
    public ApiResponse<List<AgentToolDefinition>> tools() {
        return ApiResponse.ok(toolRegistry.list());
    }

    @GetMapping("/tools/openai")
    public ApiResponse<List<Map<String, Object>>> openAiTools() {
        return ApiResponse.ok(toolRegistry.openAiTools());
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
