package com.agentpilot.agent.controller;

import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.orchestrator.AgentOrchestrator;
import com.agentpilot.agent.service.AgentConfirmationService;
import com.agentpilot.agent.service.AgentExecutionTraceService;
import com.agentpilot.agent.service.AgentRunService;
import com.agentpilot.agent.service.AgentToolCallService;
import com.agentpilot.agent.tool.AgentToolDefinition;
import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.agent.vo.AgentChatRequest;
import com.agentpilot.agent.vo.AgentChatResponse;
import com.agentpilot.agent.vo.AgentExecutionTrace;
import com.agentpilot.agent.vo.ConfirmationDecisionRequest;
import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.common.response.PageResponse;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.CustomerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasAuthority('agent:use')")
public class AgentController {
    private final AgentOrchestrator orchestrator;
    private final AgentConfirmationService confirmationService;
    private final AgentRunService runService;
    private final AgentToolCallService toolCallService;
    private final AgentExecutionTraceService executionTraceService;
    private final ToolRegistry toolRegistry;
    private final CustomerService customerService;

    public AgentController(
            AgentOrchestrator orchestrator,
            AgentConfirmationService confirmationService,
            AgentRunService runService,
            AgentToolCallService toolCallService,
            AgentExecutionTraceService executionTraceService,
            ToolRegistry toolRegistry,
            CustomerService customerService
    ) {
        this.orchestrator = orchestrator;
        this.confirmationService = confirmationService;
        this.runService = runService;
        this.toolCallService = toolCallService;
        this.executionTraceService = executionTraceService;
        this.toolRegistry = toolRegistry;
        this.customerService = customerService;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        Long currentUserId = CurrentUser.userId();
        Long currentSalesRepId = CurrentUser.salesRepId();
        if (!Objects.equals(request.userId(), currentUserId)) {
            throw new AccessDeniedException("request.userId does not match authenticated user");
        }
        if (request.salesRepId() != null && !Objects.equals(request.salesRepId(), currentSalesRepId)) {
            throw new AccessDeniedException("request.salesRepId is outside current data scope");
        }
        if (request.customerId() != null) {
            requireCustomerVisible(request.customerId(), currentSalesRepId);
        }
        AgentChatRequest securedRequest = new AgentChatRequest(
                request.sessionId(),
                currentUserId,
                currentSalesRepId,
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
        return ApiResponse.ok(orchestrator.confirm(id, currentUserId, CurrentUser.salesRepId()));
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
        return ApiResponse.ok(orchestrator.reject(id, currentUserId, CurrentUser.salesRepId()));
    }

    @GetMapping("/confirmations")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<List<AgentConfirmation>> confirmations(
            @RequestParam(defaultValue = "PENDING") String status
    ) {
        List<Long> visibleRunIds = visibleRunIds();
        if (visibleRunIds.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        LambdaQueryWrapper<AgentConfirmation> wrapper = new LambdaQueryWrapper<AgentConfirmation>()
                .in(AgentConfirmation::getRunId, visibleRunIds)
                .orderByDesc(AgentConfirmation::getId);
        if (!"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(AgentConfirmation::getStatus, status.toUpperCase());
        }
        return ApiResponse.ok(confirmationService.list(wrapper));
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
        return ApiResponse.ok(runService.list(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, CurrentUser.userId())
                .eq(AgentRun::getSalesRepId, CurrentUser.salesRepId())
                .orderByDesc(AgentRun::getId)));
    }

    @GetMapping("/runs/page")
    public ApiResponse<PageResponse<AgentRun>> runPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String keyword
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (safePage - 1) * safePageSize;
        LambdaQueryWrapper<AgentRun> countWrapper = runQuery(status, keyword);
        long total = runService.count(countWrapper);
        List<AgentRun> items = runService.list(runQuery(status, keyword)
                .orderByDesc(AgentRun::getId)
                .last("limit " + safePageSize + " offset " + offset));
        return ApiResponse.ok(new PageResponse<>(items, total, safePage, safePageSize));
    }

    @GetMapping("/runs/{runId}/tool-calls")
    public ApiResponse<List<AgentToolCall>> toolCalls(@PathVariable Long runId) {
        requireRunVisible(runId);
        return ApiResponse.ok(toolCallService.listByRunId(runId));
    }

    @GetMapping("/runs/{runId}/execution")
    public ApiResponse<AgentExecutionTrace> execution(@PathVariable Long runId) {
        requireRunVisible(runId);
        return ApiResponse.ok(executionTraceService.build(runId));
    }

    private List<Long> visibleRunIds() {
        return runService.list(new LambdaQueryWrapper<AgentRun>()
                        .select(AgentRun::getId)
                        .eq(AgentRun::getUserId, CurrentUser.userId())
                        .eq(AgentRun::getSalesRepId, CurrentUser.salesRepId()))
                .stream()
                .map(AgentRun::getId)
                .toList();
    }

    private LambdaQueryWrapper<AgentRun> runQuery(String status, String keyword) {
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, CurrentUser.userId())
                .eq(AgentRun::getSalesRepId, CurrentUser.salesRepId());
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(AgentRun::getStatus, status.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(AgentRun::getUserInput, value)
                    .or()
                    .like(AgentRun::getAgentOutput, value)
                    .or()
                    .like(AgentRun::getIntent, value));
        }
        return wrapper;
    }

    private void requireRunVisible(Long runId) {
        AgentRun run = runService.getById(runId);
        if (run == null
                || !Objects.equals(run.getUserId(), CurrentUser.userId())
                || !Objects.equals(run.getSalesRepId(), CurrentUser.salesRepId())) {
            throw new AccessDeniedException("run is outside current data scope");
        }
    }

    private void requireCustomerVisible(Long customerId, Long currentSalesRepId) {
        Customer customer = customerService.getById(customerId);
        if (customer == null || !Objects.equals(customer.getOwnerSalesRepId(), currentSalesRepId)) {
            throw new AccessDeniedException("customer is outside current data scope");
        }
    }
}
