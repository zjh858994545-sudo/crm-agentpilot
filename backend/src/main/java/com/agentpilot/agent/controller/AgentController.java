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
import com.agentpilot.common.security.DataMasking;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
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
        String currentTenantId = CurrentUser.tenantId();
        Long currentSalesRepId = CurrentUser.salesRepId();
        Long requestedSalesRepId = request.salesRepId() == null ? currentSalesRepId : request.salesRepId();
        if (!Objects.equals(request.userId(), currentUserId)) {
            throw new AccessDeniedException("request.userId does not match authenticated user");
        }
        if (!CurrentUser.canAccessSalesRep(requestedSalesRepId)) {
            throw new AccessDeniedException("request.salesRepId is outside current data scope");
        }
        if (request.customerId() != null) {
            requireCustomerVisible(request.customerId());
        }
        AgentChatRequest securedRequest = new AgentChatRequest(
                request.sessionId(),
                currentUserId,
                currentTenantId,
                requestedSalesRepId,
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
        return ApiResponse.ok(orchestrator.confirm(
                id,
                currentUserId,
                CurrentUser.tenantId(),
                CurrentUser.salesRepId(),
                CurrentUser.isManagerOrAdmin()
        ));
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
        return ApiResponse.ok(orchestrator.reject(
                id,
                currentUserId,
                CurrentUser.tenantId(),
                CurrentUser.salesRepId(),
                CurrentUser.isManagerOrAdmin()
        ));
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

    @GetMapping("/confirmations/page")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<PageResponse<AgentConfirmation>> confirmationPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "") String keyword
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (safePage - 1) * safePageSize;
        long total = confirmationService.count(confirmationQuery(status, keyword));
        List<AgentConfirmation> items = confirmationService.list(confirmationQuery(status, keyword)
                .orderByDesc(AgentConfirmation::getId)
                .last("limit " + safePageSize + " offset " + offset));
        return ApiResponse.ok(new PageResponse<>(items, total, safePage, safePageSize));
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
        return ApiResponse.ok(runService.list(runQuery("ALL", "")
                .orderByDesc(AgentRun::getId)
                .last("limit 50")));
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

    @GetMapping(value = "/runs/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('events:read')")
    public ResponseEntity<String> exportRuns(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        List<AgentRun> items = runService.list(runQuery(status, keyword)
                .orderByDesc(AgentRun::getId)
                .last("limit " + safeLimit));
        String csv = buildRunCsv(items);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agent-runs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
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
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .select(AgentRun::getId)
                .eq(AgentRun::getTenantId, CurrentUser.tenantId());
        if (!CurrentUser.isManagerOrAdmin()) {
            wrapper.eq(AgentRun::getUserId, CurrentUser.userId())
                    .eq(AgentRun::getSalesRepId, CurrentUser.salesRepId());
        }
        return runService.list(wrapper)
                .stream()
                .map(AgentRun::getId)
                .toList();
    }

    private LambdaQueryWrapper<AgentConfirmation> confirmationQuery(String status, String keyword) {
        String tenantId = CurrentUser.tenantId().replace("'", "''");
        String visibleRunSql = "select id from crm_agent_run where tenant_id = '" + tenantId + "'";
        if (!CurrentUser.isManagerOrAdmin()) {
            visibleRunSql += " and user_id = " + CurrentUser.userId() + " and sales_rep_id = " + CurrentUser.salesRepId();
        }
        LambdaQueryWrapper<AgentConfirmation> wrapper = new LambdaQueryWrapper<AgentConfirmation>()
                .inSql(AgentConfirmation::getRunId, visibleRunSql);
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(AgentConfirmation::getStatus, status.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(AgentConfirmation::getActionSummary, value)
                    .or()
                    .like(AgentConfirmation::getActionType, value)
                    .or()
                    .like(AgentConfirmation::getPayloadJson, value));
        }
        return wrapper;
    }

    private LambdaQueryWrapper<AgentRun> runQuery(String status, String keyword) {
        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getTenantId, CurrentUser.tenantId());
        if (!CurrentUser.isManagerOrAdmin()) {
            wrapper.eq(AgentRun::getUserId, CurrentUser.userId())
                    .eq(AgentRun::getSalesRepId, CurrentUser.salesRepId());
        }
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
                || !Objects.equals(run.getTenantId(), CurrentUser.tenantId())
                || (!CurrentUser.isManagerOrAdmin()
                && (!Objects.equals(run.getUserId(), CurrentUser.userId())
                || !Objects.equals(run.getSalesRepId(), CurrentUser.salesRepId())))) {
            throw new AccessDeniedException("run is outside current data scope");
        }
    }

    private void requireCustomerVisible(Long customerId) {
        Customer customer = customerService.getById(customerId);
        if (customer == null
                || !Objects.equals(customer.getTenantId(), CurrentUser.tenantId())
                || !CurrentUser.canAccessSalesRep(customer.getOwnerSalesRepId())) {
            throw new AccessDeniedException("customer is outside current data scope");
        }
    }

    private String buildRunCsv(List<AgentRun> items) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("runId,tenantId,userId,salesRepId,customerId,intent,status,modelName,latencyMs,completedAt,userInput,agentOutput,errorMessage\n");
        for (AgentRun run : items) {
            builder.append(csv(run.getId())).append(',')
                    .append(csv(run.getTenantId())).append(',')
                    .append(csv(run.getUserId())).append(',')
                    .append(csv(run.getSalesRepId())).append(',')
                    .append(csv(run.getCustomerId())).append(',')
                    .append(csv(run.getIntent())).append(',')
                    .append(csv(run.getStatus())).append(',')
                    .append(csv(run.getModelName())).append(',')
                    .append(csv(run.getLatencyMs())).append(',')
                    .append(csv(run.getCompletedAt())).append(',')
                    .append(csv(maskedSnippet(run.getUserInput()))).append(',')
                    .append(csv(maskedSnippet(run.getAgentOutput()))).append(',')
                    .append(csv(maskedSnippet(run.getErrorMessage()))).append('\n');
        }
        return builder.toString();
    }

    private String maskedSnippet(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String masked = DataMasking.maskSensitiveText(value);
        return masked.length() > 240 ? masked.substring(0, 240) + "..." : masked;
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}
