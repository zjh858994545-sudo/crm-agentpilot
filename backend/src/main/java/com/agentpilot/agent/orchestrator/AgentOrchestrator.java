package com.agentpilot.agent.orchestrator;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentSession;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.memory.ShortTermMemoryService;
import com.agentpilot.agent.service.AgentConfirmationService;
import com.agentpilot.agent.service.AgentRunService;
import com.agentpilot.agent.service.AgentSessionService;
import com.agentpilot.agent.service.AgentToolCallService;
import com.agentpilot.agent.tool.AgentToolDefinition;
import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.agent.tool.ToolType;
import com.agentpilot.agent.vo.AgentChatRequest;
import com.agentpilot.agent.vo.AgentChatResponse;
import com.agentpilot.agent.vo.ToolCallView;
import com.agentpilot.crm.entity.ContactLog;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.entity.ProductPackage;
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CrmTaskService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.crm.service.ProductPackageService;
import com.agentpilot.events.AgentPilotEventPublisher;
import com.agentpilot.model.ChatModelClient;
import com.agentpilot.model.ModelToolCall;
import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class AgentOrchestrator {
    private static final Set<String> ALLOWED_LEAD_STAGES = Set.of(
            "NEW",
            "CONTACTED",
            "QUALIFIED",
            "NEGOTIATING",
            "OBJECTION",
            "FOLLOWING",
            "WON",
            "LOST"
    );

    private final AgentSessionService sessionService;
    private final AgentRunService runService;
    private final AgentToolCallService toolCallService;
    private final AgentConfirmationService confirmationService;
    private final ShortTermMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final CustomerService customerService;
    private final ContactLogService contactLogService;
    private final LeadService leadService;
    private final CrmTaskService taskService;
    private final ProductPackageService productPackageService;
    private final LeadScoringService leadScoringService;
    private final RagService ragService;
    private final ChatModelClient chatModelClient;
    private final AgentPilotEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(
            AgentSessionService sessionService,
            AgentRunService runService,
            AgentToolCallService toolCallService,
            AgentConfirmationService confirmationService,
            ShortTermMemoryService memoryService,
            ToolRegistry toolRegistry,
            CustomerService customerService,
            ContactLogService contactLogService,
            LeadService leadService,
            CrmTaskService taskService,
            ProductPackageService productPackageService,
            LeadScoringService leadScoringService,
            RagService ragService,
            ChatModelClient chatModelClient,
            AgentPilotEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.runService = runService;
        this.toolCallService = toolCallService;
        this.confirmationService = confirmationService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.customerService = customerService;
        this.contactLogService = contactLogService;
        this.leadService = leadService;
        this.taskService = taskService;
        this.productPackageService = productPackageService;
        this.leadScoringService = leadScoringService;
        this.ragService = ragService;
        this.chatModelClient = chatModelClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        Instant startedAt = Instant.now();
        AgentSession session = getOrCreateSession(request);
        memoryService.append(session.getId(), "user", request.message());
        AgentRun run = createRun(request, session);

        String ruleIntent = routeIntent(request.message());
        AgentChatResponse response = requiresDeterministicWriteFlow(ruleIntent)
                ? routeByRules(request, session, run, ruleIntent)
                : tryLlmToolCalling(request, session, run)
                .orElseGet(() -> routeByRules(request, session, run, ruleIntent));
        run.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
        run.setCompletedAt(LocalDateTime.now());
        runService.updateById(run);
        eventPublisher.publishAgentRunCompleted(run);
        memoryService.append(session.getId(), "assistant", response.answer());
        return response;
    }

    private Optional<AgentChatResponse> tryLlmToolCalling(AgentChatRequest request, AgentSession session, AgentRun run) {
        if (!chatModelClient.configured()) {
            return Optional.empty();
        }
        Optional<ModelToolCall> decision = chatModelClient.chooseTool(toolRoutingSystemPrompt(), request.message(), toolRegistry.openAiTools());
        if (decision.isEmpty() || toolRegistry.find(decision.get().name()).isEmpty()) {
            return Optional.empty();
        }
        run.setIntent("LLM_TOOL:" + decision.get().name());
        runService.updateById(run);
        return executeModelToolCall(decision.get(), request, session, run);
    }

    private AgentChatResponse routeByRules(AgentChatRequest request, AgentSession session, AgentRun run) {
        return routeByRules(request, session, run, routeIntent(request.message()));
    }

    private AgentChatResponse routeByRules(AgentChatRequest request, AgentSession session, AgentRun run, String intent) {
        run.setIntent(intent);
        runService.updateById(run);
        return switch (intent) {
            case "LEAD_RECOMMENDATION" -> recommendLeads(request, session, run, 5);
            case "CUSTOMER_ANALYSIS" -> analyzeCustomer(request, session, run);
            case "CREATE_TASK" -> proposeTask(request, session, run, Map.of());
            case "WRITE_CONTACT_LOG" -> proposeContactLog(request, session, run, Map.of());
            case "UPDATE_LEAD_STAGE" -> proposeLeadStageUpdate(request, session, run, Map.of());
            case "KNOWLEDGE_QA" -> answerKnowledge(request, session, run, request.message(), 5);
            default -> fallback(session, run);
        };
    }

    @Transactional
    public Map<String, Object> confirm(Long confirmationId, Long userId, String tenantId, Long salesRepId) {
        requireUserId(userId);
        AgentConfirmation confirmation = confirmationService.getById(confirmationId);
        if (confirmation == null) {
            return Map.of("status", "NOT_FOUND");
        }
        requireConfirmationOwner(confirmation, userId, tenantId, salesRepId);
        if (!Objects.equals(confirmation.getStatus(), "PENDING")) {
            return Map.of("status", confirmation.getStatus(), "confirmationId", confirmationId);
        }
        if (!confirmationService.claimPendingForConfirm(confirmationId, userId)) {
            AgentConfirmation latest = confirmationService.getById(confirmationId);
            return Map.of(
                    "status", latest == null ? "NOT_FOUND" : latest.getStatus(),
                    "confirmationId", confirmationId
            );
        }
        confirmation = confirmationService.getById(confirmationId);

        Object result = executeConfirmedAction(confirmation);
        if (result instanceof CrmTask task) {
            eventPublisher.publishCrmTaskCreated(task);
        }
        confirmation.setStatus("CONFIRMED");
        confirmation.setConfirmedBy(userId);
        confirmation.setConfirmedAt(LocalDateTime.now());
        confirmationService.updateById(confirmation);

        AgentToolCall toolCall = toolCallService.getById(confirmation.getToolCallId());
        if (toolCall != null) {
            toolCall.setStatus("SUCCESS");
            toolCall.setOutputJson(toJson(result));
            toolCall.setCompletedAt(LocalDateTime.now());
            toolCallService.updateById(toolCall);
        }
        return Map.of("status", "CONFIRMED", "confirmationId", confirmationId, "result", result);
    }

    @Transactional
    public Map<String, Object> reject(Long confirmationId, Long userId, String tenantId, Long salesRepId) {
        requireUserId(userId);
        AgentConfirmation confirmation = confirmationService.getById(confirmationId);
        if (confirmation == null) {
            return Map.of("status", "NOT_FOUND");
        }
        requireConfirmationOwner(confirmation, userId, tenantId, salesRepId);
        if (!Objects.equals(confirmation.getStatus(), "PENDING")) {
            return Map.of("status", confirmation.getStatus(), "confirmationId", confirmationId);
        }
        if (!confirmationService.rejectPending(confirmationId, userId)) {
            AgentConfirmation latest = confirmationService.getById(confirmationId);
            return Map.of(
                    "status", latest == null ? "NOT_FOUND" : latest.getStatus(),
                    "confirmationId", confirmationId
            );
        }
        return Map.of("status", "REJECTED", "confirmationId", confirmationId);
    }

    private Optional<AgentChatResponse> executeModelToolCall(
            ModelToolCall decision,
            AgentChatRequest request,
            AgentSession session,
            AgentRun run
    ) {
        Map<String, Object> args = decision.arguments() == null ? Map.of() : decision.arguments();
        return switch (decision.name()) {
            case "rankLeads" -> Optional.of(recommendLeads(request, session, run, intArg(args, "topK", 5)));
            case "analyzeCustomer" -> Optional.of(analyzeCustomer(request, session, run, resolveCustomer(request, args)));
            case "queryCustomerProfile" -> Optional.of(queryCustomerProfile(request, session, run, args));
            case "queryContactHistory" -> Optional.of(queryContactHistory(request, session, run, args));
            case "searchKnowledge" -> Optional.of(answerKnowledge(
                    request,
                    session,
                    run,
                    stringArg(args, "query", request.message()),
                    intArg(args, "topK", 5)
            ));
            case "queryProductPackage" -> Optional.of(queryProductPackage(request, session, run, args));
            case "createFollowupTask" -> Optional.of(proposeTask(request, session, run, args));
            case "writeContactLog" -> Optional.of(proposeContactLog(request, session, run, args));
            case "updateLeadStage" -> Optional.of(proposeLeadStageUpdate(request, session, run, args));
            default -> Optional.empty();
        };
    }

    private AgentChatResponse recommendLeads(AgentChatRequest request, AgentSession session, AgentRun run, int topK) {
        int limit = Math.max(1, Math.min(topK, 20));
        Map<String, Object> input = Map.of(
                "tenantId", defaultTenantId(request.tenantId()),
                "salesRepId", defaultSalesRepId(request.salesRepId()),
                "topK", limit
        );
        Instant toolStartedAt = Instant.now();
        List<LeadRecommendation> recommendations = leadScoringService.recommend(
                defaultTenantId(request.tenantId()),
                defaultSalesRepId(request.salesRepId()),
                limit
        );
        AgentToolCall call = recordTool(run.getId(), "rankLeads", input, recommendations, "SUCCESS", null, elapsedMs(toolStartedAt));
        String answer = "建议今天优先跟进：" + recommendations.stream()
                .limit(3)
                .map(item -> item.customerName() + "(" + item.priority() + "，" + item.score() + ")")
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无推荐商机");
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(call)));
    }

    private AgentChatResponse analyzeCustomer(AgentChatRequest request, AgentSession session, AgentRun run) {
        return analyzeCustomer(request, session, run, resolveCustomer(request));
    }

    private AgentChatResponse analyzeCustomer(AgentChatRequest request, AgentSession session, AgentRun run, Customer customer) {
        Instant profileStartedAt = Instant.now();
        if (customer == null) {
            String answer = "没有找到对应客户，请提供客户名称或 customerId。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        AgentToolCall profileCall = recordTool(run.getId(), "queryCustomerProfile", Map.of("customerId", customer.getId()), customer, "SUCCESS", null, elapsedMs(profileStartedAt));
        Instant historyStartedAt = Instant.now();
        List<ContactLog> logs = contactLogService.listByCustomerId(customer.getId(), defaultTenantId(request.tenantId()));
        AgentToolCall historyCall = recordTool(run.getId(), "queryContactHistory", Map.of("customerId", customer.getId()), logs, "SUCCESS", null, elapsedMs(historyStartedAt));
        Instant knowledgeStartedAt = Instant.now();
        KnowledgeAnswer knowledge = ragService.ask(customer.getIndustry() + " 续费 价格异议 跟进策略", 3);
        AgentToolCall knowledgeCall = recordTool(run.getId(), "searchKnowledge", Map.of("query", customer.getIndustry() + " 续费 价格异议 跟进策略"), knowledge, "SUCCESS", null, elapsedMs(knowledgeStartedAt));

        String deterministicAnswer = "客户现状：" + customer.getName() + " 属于 " + customer.getValueLevel()
                + " 类客户，风险等级 " + customer.getRiskLevel()
                + "。主要动作：复盘历史跟进和曝光效果，围绕客户关注点给出续费/优化建议。建议话术：先确认经营目标，再用数据和同行案例说明方案价值。";
        String answer = generateCustomerAnalysisAnswer(session.getId(), request.message(), customer, logs, knowledge, deterministicAnswer);
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(profileCall), view(historyCall), view(knowledgeCall)));
    }

    private AgentChatResponse proposeTask(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        Customer customer = resolveCustomer(request, args);
        if (customer == null) {
            String answer = "没有找到对应客户，暂不能生成 CRM 任务确认。请提供准确客户名称或 customerId。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        Lead lead = leadService.list(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getTenantId, defaultTenantId(request.tenantId()))
                .eq(Lead::getCustomerId, customer.getId())
                .eq(Lead::getSalesRepId, defaultSalesRepId(request.salesRepId()))
                .orderByDesc(Lead::getScore)
                .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
        LocalDateTime dueTime = dateTimeArg(args, "dueTime", LocalDateTime.now().plusDays(1).with(LocalTime.of(10, 0)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", defaultTenantId(request.tenantId()));
        payload.put("customerId", customer.getId());
        payload.put("leadId", lead == null ? null : lead.getId());
        payload.put("salesRepId", defaultSalesRepId(request.salesRepId()));
        String title = stringArg(args, "title", "跟进" + customer.getName() + "续费意向");
        String idempotencyKey = "agent-task-" + customer.getId() + "-"
                + dueTime.toLocalDate() + "-"
                + Integer.toHexString(Objects.hash(customer.getId(), title, dueTime));
        payload.put("title", title);
        payload.put("content", stringArg(args, "content", "围绕套餐到期、曝光效果、价格异议和下一步复盘沟通。"));
        payload.put("dueTime", dueTime.toString());
        payload.put("idempotencyKey", idempotencyKey);

        AgentToolCall call = recordTool(run.getId(), "createFollowupTask", payload, Map.of("status", "NEED_CONFIRMATION"), "NEED_CONFIRMATION", null, 0L);
        AgentConfirmation confirmation = new AgentConfirmation();
        confirmation.setRunId(run.getId());
        confirmation.setToolCallId(call.getId());
        confirmation.setActionType("CREATE_FOLLOWUP_TASK");
        confirmation.setActionSummary("创建" + dueTime + "跟进" + customer.getName() + "的任务");
        confirmation.setPayloadJson(toJson(payload));
        confirmation.setStatus("PENDING");
        confirmation.setExpiredAt(LocalDateTime.now().plusHours(24));
        confirmationService.save(confirmation);

        call.setConfirmationId(confirmation.getId());
        call.setOutputJson(toJson(Map.of("confirmationId", confirmation.getId())));
        toolCallService.updateById(call);

        run.setStatus("NEED_CONFIRMATION");
        run.setAgentOutput("需要你确认后才能创建 CRM 任务。");
        return new AgentChatResponse(
                "confirmation_required",
                session.getId(),
                run.getId(),
                "需要你确认后才能创建 CRM 任务。",
                confirmation.getId(),
                confirmation.getActionSummary(),
                payload,
                List.of(view(call))
        );
    }

    private AgentChatResponse answerKnowledge(AgentChatRequest request, AgentSession session, AgentRun run, String query, int topK) {
        Instant toolStartedAt = Instant.now();
        KnowledgeAnswer answer = ragService.ask(query, Math.max(1, Math.min(topK, 20)));
        AgentToolCall call = recordTool(run.getId(), "searchKnowledge", Map.of("query", query), answer, "SUCCESS", null, elapsedMs(toolStartedAt));
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer.answer());
        return finalAnswer(session.getId(), run.getId(), answer.answer(), List.of(view(call)));
    }

    private AgentChatResponse queryCustomerProfile(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        Customer customer = resolveCustomer(request, args);
        if (customer == null) {
            String answer = "没有找到对应客户，请提供客户名称或 customerId。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        Instant toolStartedAt = Instant.now();
        AgentToolCall call = recordTool(run.getId(), "queryCustomerProfile", Map.of("customerId", customer.getId()), customer, "SUCCESS", null, elapsedMs(toolStartedAt));
        String answer = "客户 " + customer.getName() + " 属于 " + customer.getIndustry()
                + " 行业，价值等级 " + customer.getValueLevel()
                + "，风险等级 " + customer.getRiskLevel()
                + "，标签：" + customer.getTags();
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(call)));
    }

    private AgentChatResponse queryContactHistory(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        Customer customer = resolveCustomer(request, args);
        if (customer == null) {
            String answer = "没有找到对应客户，请提供客户名称或 customerId。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        Instant toolStartedAt = Instant.now();
        List<ContactLog> logs = contactLogService.listByCustomerId(customer.getId(), defaultTenantId(request.tenantId()));
        AgentToolCall call = recordTool(run.getId(), "queryContactHistory", Map.of("customerId", customer.getId()), logs, "SUCCESS", null, elapsedMs(toolStartedAt));
        String answer = customer.getName() + " 最近跟进：\n" + summarizeLogs(logs);
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(call)));
    }

    private AgentChatResponse queryProductPackage(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        String industry = stringArg(args, "industry", "");
        Instant toolStartedAt = Instant.now();
        List<ProductPackage> packages = productPackageService.list()
                .stream()
                .filter(item -> industry.isBlank() || (item.getIndustry() != null && item.getIndustry().contains(industry)))
                .limit(5)
                .toList();
        AgentToolCall call = recordTool(run.getId(), "queryProductPackage", Map.of("industry", industry), packages, "SUCCESS", null, elapsedMs(toolStartedAt));
        String answer = packages.isEmpty()
                ? "没有找到匹配的套餐政策。"
                : "匹配套餐：" + packages.stream()
                .map(item -> item.getName() + "(" + item.getIndustry() + "，" + item.getPrice() + "元)")
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(call)));
    }

    private AgentChatResponse proposeContactLog(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        Customer customer = resolveCustomer(request, args);
        if (customer == null) {
            String answer = "没有找到对应客户，暂不能生成联系记录写入确认。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        Lead lead = leadService.list(new LambdaQueryWrapper<Lead>()
                        .eq(Lead::getTenantId, defaultTenantId(request.tenantId()))
                        .eq(Lead::getCustomerId, customer.getId())
                        .eq(Lead::getSalesRepId, defaultSalesRepId(request.salesRepId()))
                        .orderByDesc(Lead::getScore)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", defaultTenantId(request.tenantId()));
        String content = stringArg(args, "content", request.message());
        LocalDateTime contactAt = dateTimeArg(args, "contactAt", LocalDateTime.now());
        String idempotencyKey = "agent-contact-log-" + customer.getId() + "-"
                + contactAt.toLocalDate() + "-"
                + Integer.toHexString(Objects.hash(customer.getId(), lead == null ? null : lead.getId(), content, contactAt.toLocalDate()));
        payload.put("customerId", customer.getId());
        payload.put("salesRepId", defaultSalesRepId(request.salesRepId()));
        payload.put("leadId", lead == null ? null : lead.getId());
        payload.put("channel", stringArg(args, "channel", "PHONE"));
        payload.put("content", content);
        payload.put("summary", stringArg(args, "summary", "由 LLM Tool Calling 生成的联系记录写入建议"));
        payload.put("customerIntent", stringArg(args, "customerIntent", "MEDIUM"));
        payload.put("objections", stringArg(args, "objections", ""));
        payload.put("nextAction", stringArg(args, "nextAction", "继续跟进"));
        payload.put("contactAt", contactAt.toString());
        payload.put("idempotencyKey", idempotencyKey);

        AgentToolCall call = recordTool(run.getId(), "writeContactLog", payload, Map.of("status", "NEED_CONFIRMATION"), "NEED_CONFIRMATION", null, 0L);
        AgentConfirmation confirmation = createConfirmation(run, call, "WRITE_CONTACT_LOG", "写入" + customer.getName() + "的联系记录", payload);
        call.setConfirmationId(confirmation.getId());
        call.setOutputJson(toJson(Map.of("confirmationId", confirmation.getId())));
        toolCallService.updateById(call);

        run.setStatus("NEED_CONFIRMATION");
        run.setAgentOutput("需要你确认后才能写入 CRM 联系记录。");
        return new AgentChatResponse(
                "confirmation_required",
                session.getId(),
                run.getId(),
                "需要你确认后才能写入 CRM 联系记录。",
                confirmation.getId(),
                confirmation.getActionSummary(),
                payload,
                List.of(view(call))
        );
    }

    private AgentChatResponse proposeLeadStageUpdate(AgentChatRequest request, AgentSession session, AgentRun run, Map<String, Object> args) {
        Long leadId = nullableLongArg(args, "leadId");
        Lead lead = leadId == null ? null : leadService.getById(leadId);
        if (lead == null
                || !Objects.equals(lead.getTenantId(), defaultTenantId(request.tenantId()))
                || !Objects.equals(lead.getSalesRepId(), defaultSalesRepId(request.salesRepId()))) {
            String answer = "没有找到对应商机，暂不能生成商机阶段变更确认。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", defaultTenantId(request.tenantId()));
        payload.put("leadId", leadId);
        payload.put("salesRepId", defaultSalesRepId(request.salesRepId()));
        String stage = normalizeLeadStage(stringArg(args, "stage", "NEGOTIATING"));
        if (!ALLOWED_LEAD_STAGES.contains(stage)) {
            String answer = "商机阶段值不在允许范围内，暂不能生成阶段变更确认。允许值：" + ALLOWED_LEAD_STAGES;
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        payload.put("stage", stage);
        payload.put("reason", stringArg(args, "reason", "由 LLM Tool Calling 生成的商机阶段变更建议"));

        AgentToolCall call = recordTool(run.getId(), "updateLeadStage", payload, Map.of("status", "NEED_CONFIRMATION"), "NEED_CONFIRMATION", null, 0L);
        AgentConfirmation confirmation = createConfirmation(run, call, "UPDATE_LEAD_STAGE", "更新商机 " + leadId + " 阶段为 " + payload.get("stage"), payload);
        call.setConfirmationId(confirmation.getId());
        call.setOutputJson(toJson(Map.of("confirmationId", confirmation.getId())));
        toolCallService.updateById(call);

        run.setStatus("NEED_CONFIRMATION");
        run.setAgentOutput("需要你确认后才能更新 CRM 商机阶段。");
        return new AgentChatResponse(
                "confirmation_required",
                session.getId(),
                run.getId(),
                "需要你确认后才能更新 CRM 商机阶段。",
                confirmation.getId(),
                confirmation.getActionSummary(),
                payload,
                List.of(view(call))
        );
    }

    private AgentConfirmation createConfirmation(
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
        return confirmation;
    }

    private AgentChatResponse fallback(AgentSession session, AgentRun run) {
        String answer = "我可以帮你做商机推荐、客户分析、知识库问答，或在确认后创建 CRM 跟进任务。";
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of());
    }

    private Object executeConfirmedAction(AgentConfirmation confirmation) {
        if (!Objects.equals(confirmation.getActionType(), "CREATE_FOLLOWUP_TASK")) {
            if (Objects.equals(confirmation.getActionType(), "WRITE_CONTACT_LOG")) {
                return executeWriteContactLog(confirmation);
            }
            if (Objects.equals(confirmation.getActionType(), "UPDATE_LEAD_STAGE")) {
                return executeUpdateLeadStage(confirmation);
            }
            return Map.of("status", "UNSUPPORTED_ACTION");
        }
        try {
            JsonNode payload = objectMapper.readTree(confirmation.getPayloadJson());
            String idempotencyKey = payload.get("idempotencyKey").asText();
            String tenantId = stringJson(payload, "tenantId", "demo");
            CrmTask existing = taskService.getOne(new LambdaQueryWrapper<CrmTask>()
                    .eq(CrmTask::getTenantId, tenantId)
                    .eq(CrmTask::getIdempotencyKey, idempotencyKey), false);
            if (existing != null) {
                return existing;
            }
            CrmTask task = new CrmTask();
            task.setTenantId(tenantId);
            task.setCustomerId(payload.get("customerId").asLong());
            task.setLeadId(payload.get("leadId").isNull() ? null : payload.get("leadId").asLong());
            task.setSalesRepId(payload.get("salesRepId").asLong());
            task.setTitle(payload.get("title").asText());
            task.setContent(payload.get("content").asText());
            task.setDueTime(LocalDateTime.parse(payload.get("dueTime").asText()));
            task.setStatus("PENDING");
            task.setSource("AGENT");
            task.setIdempotencyKey(idempotencyKey);
            task.setVersion(0);
            taskService.save(task);
            return task;
        } catch (JsonProcessingException ex) {
            return Map.of("status", "PAYLOAD_ERROR", "message", ex.getMessage());
        }
    }

    private Object executeWriteContactLog(AgentConfirmation confirmation) {
        try {
            JsonNode payload = objectMapper.readTree(confirmation.getPayloadJson());
            String idempotencyKey = payload.hasNonNull("idempotencyKey")
                    ? payload.get("idempotencyKey").asText()
                    : legacyContactLogIdempotencyKey(payload);
            String tenantId = stringJson(payload, "tenantId", "demo");
            ContactLog existing = contactLogService.getOne(new LambdaQueryWrapper<ContactLog>()
                    .eq(ContactLog::getTenantId, tenantId)
                    .eq(ContactLog::getIdempotencyKey, idempotencyKey), false);
            if (existing != null) {
                return existing;
            }
            ContactLog log = new ContactLog();
            log.setTenantId(tenantId);
            log.setCustomerId(payload.get("customerId").asLong());
            log.setSalesRepId(payload.get("salesRepId").asLong());
            log.setLeadId(payload.get("leadId").isNull() ? null : payload.get("leadId").asLong());
            log.setChannel(payload.get("channel").asText());
            log.setContent(payload.get("content").asText());
            log.setSummary(payload.get("summary").asText());
            log.setCustomerIntent(payload.get("customerIntent").asText());
            log.setObjections(payload.get("objections").asText());
            log.setNextAction(payload.get("nextAction").asText());
            log.setContactAt(LocalDateTime.parse(payload.get("contactAt").asText()));
            log.setIdempotencyKey(idempotencyKey);
            contactLogService.save(log);
            return log;
        } catch (JsonProcessingException ex) {
            return Map.of("status", "PAYLOAD_ERROR", "message", ex.getMessage());
        }
    }

    private String legacyContactLogIdempotencyKey(JsonNode payload) {
        Long customerId = payload.get("customerId").asLong();
        Long leadId = payload.get("leadId").isNull() ? null : payload.get("leadId").asLong();
        String content = payload.get("content").asText();
        LocalDateTime contactAt = LocalDateTime.parse(payload.get("contactAt").asText());
        return "agent-contact-log-" + customerId + "-"
                + contactAt.toLocalDate() + "-"
                + Integer.toHexString(Objects.hash(customerId, leadId, content, contactAt.toLocalDate()));
    }

    private Object executeUpdateLeadStage(AgentConfirmation confirmation) {
        try {
            JsonNode payload = objectMapper.readTree(confirmation.getPayloadJson());
            Lead lead = leadService.getById(payload.get("leadId").asLong());
            if (lead == null) {
                return Map.of("status", "NOT_FOUND");
            }
            String tenantId = stringJson(payload, "tenantId", "demo");
            if (!Objects.equals(lead.getTenantId(), tenantId)) {
                return Map.of("status", "ACCESS_DENIED");
            }
            Long salesRepId = payload.hasNonNull("salesRepId") ? payload.get("salesRepId").asLong() : null;
            if (salesRepId != null && !Objects.equals(lead.getSalesRepId(), salesRepId)) {
                return Map.of("status", "ACCESS_DENIED");
            }
            String stage = normalizeLeadStage(payload.get("stage").asText());
            if (!ALLOWED_LEAD_STAGES.contains(stage)) {
                return Map.of("status", "INVALID_STAGE", "allowed", ALLOWED_LEAD_STAGES);
            }
            lead.setStage(stage);
            lead.setScoreReason(payload.get("reason").asText());
            lead.setUpdatedAt(LocalDateTime.now());
            leadService.updateById(lead);
            return lead;
        } catch (JsonProcessingException ex) {
            return Map.of("status", "PAYLOAD_ERROR", "message", ex.getMessage());
        }
    }

    private AgentSession getOrCreateSession(AgentChatRequest request) {
        if (request.sessionId() != null) {
            AgentSession existing = sessionService.getById(request.sessionId());
            if (existing != null) {
                if (!sessionVisibleTo(existing, request)) {
                    throw new AccessDeniedException("session is outside current data scope");
                }
                return existing;
            }
        }
        AgentSession session = new AgentSession();
        session.setTenantId(defaultTenantId(request.tenantId()));
        session.setUserId(defaultUserId(request.userId()));
        session.setSalesRepId(defaultSalesRepId(request.salesRepId()));
        session.setCustomerId(request.customerId());
        session.setTitle("CRM Agent 会话");
        session.setStatus("ACTIVE");
        sessionService.save(session);
        return session;
    }

    private AgentRun createRun(AgentChatRequest request, AgentSession session) {
        AgentRun run = new AgentRun();
        run.setTenantId(defaultTenantId(request.tenantId()));
        run.setSessionId(session.getId());
        run.setUserId(defaultUserId(request.userId()));
        run.setSalesRepId(defaultSalesRepId(request.salesRepId()));
        run.setCustomerId(request.customerId());
        run.setUserInput(request.message());
        run.setStatus("RECEIVED");
        run.setModelName(chatModelClient.modelName());
        runService.save(run);
        return run;
    }

    private AgentToolCall recordTool(Long runId, String toolName, Object input, Object output, String status, String errorMessage, long latencyMs) {
        AgentToolDefinition definition = toolRegistry.find(toolName)
                .orElse(new AgentToolDefinition(toolName, "unknown", ToolType.READ, false, List.of(), Map.of()));
        AgentToolCall call = new AgentToolCall();
        call.setRunId(runId);
        call.setToolName(toolName);
        call.setToolType(definition.type().name());
        call.setInputJson(toJson(input));
        call.setOutputJson(toJson(output));
        call.setStatus(status);
        call.setLatencyMs(Math.max(0L, latencyMs));
        call.setErrorMessage(errorMessage);
        call.setRequiresConfirmation(definition.requiresConfirmation());
        call.setCompletedAt(LocalDateTime.now());
        toolCallService.save(call);
        eventPublisher.publishToolCallRecorded(call);
        return call;
    }

    private String generateCustomerAnalysisAnswer(
            Long sessionId,
            String userMessage,
            Customer customer,
            List<ContactLog> logs,
            KnowledgeAnswer knowledge,
            String deterministicAnswer
    ) {
        if (!chatModelClient.configured()) {
            return deterministicAnswer;
        }
        String systemPrompt = """
                你是 CRM-AgentPilot 的销售作业助手。只能基于给定 CRM 事实、历史跟进和知识库引用生成回答。
                不要编造客户信息。输出必须包含客户现状、主要风险、推荐动作、可执行话术。
                涉及写 CRM 的动作只能建议，不能宣称已经写入。
                """;
        String userPrompt = """
                用户问题：%s

                客户资料：
                名称：%s
                行业：%s
                城市：%s
                价值等级：%s
                风险等级：%s
                标签：%s

                最近跟进摘要：
                %s

                会话短期记忆：
                %s

                知识库回答：
                %s

                请生成简洁、专业、可执行的客户跟进策略。
                """.formatted(
                userMessage,
                customer.getName(),
                customer.getIndustry(),
                customer.getCity(),
                customer.getValueLevel(),
                customer.getRiskLevel(),
                customer.getTags(),
                summarizeLogs(logs),
                memoryService.summarize(sessionId),
                knowledge.answer()
        );
        Optional<String> modelAnswer = chatModelClient.complete(systemPrompt, userPrompt);
        return modelAnswer.orElse(deterministicAnswer);
    }

    private String summarizeLogs(List<ContactLog> logs) {
        return logs.stream()
                .limit(3)
                .map(log -> "- " + log.getContactAt() + " " + log.getSummary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("暂无历史跟进记录");
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required for confirmation decisions");
        }
    }

    private void requireConfirmationOwner(AgentConfirmation confirmation, Long userId, String tenantId, Long salesRepId) {
        AgentRun run = runService.getById(confirmation.getRunId());
        if (run == null
                || !Objects.equals(run.getTenantId(), tenantId)
                || !Objects.equals(run.getUserId(), userId)
                || !Objects.equals(run.getSalesRepId(), salesRepId)) {
            throw new AccessDeniedException("confirmation is outside current data scope");
        }
    }

    private String toolRoutingSystemPrompt() {
        return """
                你是 CRM-AgentPilot 的 LLM Tool Calling 路由器。
                你的任务是根据用户请求选择一个最合适的工具，并填入可确定的参数。
                客户分析、客户画像、跟进策略类问题优先选择 analyzeCustomer。
                商机优先级、今天跟进谁选择 rankLeads。
                销售 SOP、政策、质检、话术问题选择 searchKnowledge。
                创建任务、写联系记录、更新商机阶段属于 CRM 写操作，也要选择对应写工具；系统会生成人工确认，不会直接落库。
                如果只知道客户名称不知道 customerId，请填 customerName。
                当前时间：%s。
                """.formatted(LocalDateTime.now());
    }

    private String routeIntent(String message) {
        if (message.contains("创建") && message.contains("任务")) {
            return "CREATE_TASK";
        }
        if ((message.contains("写入") || message.contains("记录")) && message.contains("联系记录")) {
            return "WRITE_CONTACT_LOG";
        }
        if ((message.contains("更新") || message.contains("修改")) && message.contains("商机") && message.contains("阶段")) {
            return "UPDATE_LEAD_STAGE";
        }
        if (message.contains("优先") || message.contains("跟进哪些") || message.contains("跟进谁")) {
            return "LEAD_RECOMMENDATION";
        }
        if (message.contains("分析") || message.contains("客户")) {
            return "CUSTOMER_ANALYSIS";
        }
        if (message.contains("怎么") || message.contains("能不能") || message.contains("政策") || message.contains("质检")) {
            return "KNOWLEDGE_QA";
        }
        return "GENERAL";
    }

    private boolean requiresDeterministicWriteFlow(String intent) {
        return Set.of("CREATE_TASK", "WRITE_CONTACT_LOG", "UPDATE_LEAD_STAGE").contains(intent);
    }

    private Customer resolveCustomer(AgentChatRequest request) {
        if (request.customerId() != null) {
            Customer customer = customerService.getById(request.customerId());
            return customerVisibleTo(customer, defaultTenantId(request.tenantId()), defaultSalesRepId(request.salesRepId())) ? customer : null;
        }
        String message = request.message();
        return customerService.findMentionedIn(message, defaultTenantId(request.tenantId()), defaultSalesRepId(request.salesRepId())).orElse(null);
    }

    private Customer resolveCustomer(AgentChatRequest request, Map<String, Object> args) {
        String tenantId = defaultTenantId(request.tenantId());
        Long salesRepId = defaultSalesRepId(request.salesRepId());
        Long customerId = nullableLongArg(args, "customerId");
        if (customerId != null) {
            Customer customer = customerService.getById(customerId);
            if (customerVisibleTo(customer, tenantId, salesRepId)) {
                return customer;
            }
        }
        String customerName = stringArg(args, "customerName", "");
        if (!customerName.isBlank()) {
            Optional<Customer> customer = customerService.findMentionedIn(customerName, tenantId, salesRepId);
            if (customer.isPresent()) {
                return customer.get();
            }
        }
        return resolveCustomer(request);
    }

    private boolean customerVisibleTo(Customer customer, String tenantId, Long salesRepId) {
        return customer != null
                && Objects.equals(customer.getTenantId(), tenantId)
                && Objects.equals(customer.getOwnerSalesRepId(), salesRepId);
    }

    private boolean sessionVisibleTo(AgentSession session, AgentChatRequest request) {
        return Objects.equals(session.getTenantId(), defaultTenantId(request.tenantId()))
                && Objects.equals(session.getUserId(), defaultUserId(request.userId()))
                && Objects.equals(session.getSalesRepId(), defaultSalesRepId(request.salesRepId()));
    }

    private String stringArg(Map<String, Object> args, String key, String fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? fallback : text;
    }

    private String stringJson(JsonNode payload, String key, String fallback) {
        if (payload == null || !payload.hasNonNull(key)) {
            return fallback;
        }
        String value = payload.get(key).asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeLeadStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return "NEGOTIATING";
        }
        return stage.trim().toUpperCase();
    }

    private int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Long nullableLongArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank() && !"null".equalsIgnoreCase(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime dateTimeArg(Map<String, Object> args, String key, LocalDateTime fallback) {
        String value = stringArg(args, key, "");
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private AgentChatResponse finalAnswer(Long sessionId, Long runId, String answer, List<ToolCallView> toolCalls) {
        return new AgentChatResponse("final_answer", sessionId, runId, answer, null, null, null, toolCalls);
    }

    private ToolCallView view(AgentToolCall call) {
        return new ToolCallView(
                call.getId(),
                call.getToolName(),
                call.getToolType(),
                call.getStatus(),
                Boolean.TRUE.equals(call.getRequiresConfirmation()),
                call.getConfirmationId()
        );
    }

    private Long defaultUserId(Long userId) {
        return userId == null ? 1L : userId;
    }

    private String defaultTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "demo" : tenantId;
    }

    private Long defaultSalesRepId(Long salesRepId) {
        return salesRepId == null ? 1L : salesRepId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
