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
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CrmTaskService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.crm.service.ProductPackageService;
import com.agentpilot.events.AgentPilotEventPublisher;
import com.agentpilot.model.ChatModelClient;
import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class AgentOrchestrator {
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

    @Transactional
    public AgentChatResponse chat(AgentChatRequest request) {
        Instant startedAt = Instant.now();
        AgentSession session = getOrCreateSession(request);
        memoryService.append(session.getId(), "user", request.message());
        AgentRun run = createRun(request, session);
        String intent = routeIntent(request.message());
        run.setIntent(intent);
        runService.updateById(run);

        AgentChatResponse response = switch (intent) {
            case "LEAD_RECOMMENDATION" -> recommendLeads(request, session, run);
            case "CUSTOMER_ANALYSIS" -> analyzeCustomer(request, session, run);
            case "CREATE_TASK" -> proposeTask(request, session, run);
            case "KNOWLEDGE_QA" -> answerKnowledge(request, session, run);
            default -> fallback(session, run);
        };
        run.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
        run.setCompletedAt(LocalDateTime.now());
        runService.updateById(run);
        eventPublisher.publishAgentRunCompleted(run);
        memoryService.append(session.getId(), "assistant", response.answer());
        return response;
    }

    @Transactional
    public Map<String, Object> confirm(Long confirmationId, Long userId) {
        AgentConfirmation confirmation = confirmationService.getById(confirmationId);
        if (confirmation == null) {
            return Map.of("status", "NOT_FOUND");
        }
        if (!Objects.equals(confirmation.getStatus(), "PENDING")) {
            return Map.of("status", confirmation.getStatus(), "confirmationId", confirmationId);
        }

        Object result = executeConfirmedAction(confirmation);
        if (result instanceof CrmTask task) {
            eventPublisher.publishCrmTaskCreated(task);
        }
        confirmation.setStatus("CONFIRMED");
        confirmation.setConfirmedBy(userId == null ? 1L : userId);
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
    public Map<String, Object> reject(Long confirmationId, Long userId) {
        AgentConfirmation confirmation = confirmationService.getById(confirmationId);
        if (confirmation == null) {
            return Map.of("status", "NOT_FOUND");
        }
        confirmation.setStatus("REJECTED");
        confirmation.setConfirmedBy(userId == null ? 1L : userId);
        confirmation.setConfirmedAt(LocalDateTime.now());
        confirmationService.updateById(confirmation);
        return Map.of("status", "REJECTED", "confirmationId", confirmationId);
    }

    private AgentChatResponse recommendLeads(AgentChatRequest request, AgentSession session, AgentRun run) {
        Map<String, Object> input = Map.of("salesRepId", defaultSalesRepId(request.salesRepId()), "topK", 5);
        List<LeadRecommendation> recommendations = leadScoringService.recommend(defaultSalesRepId(request.salesRepId()), 5);
        AgentToolCall call = recordTool(run.getId(), "rankLeads", input, recommendations, "SUCCESS", null);
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
        Customer customer = resolveCustomer(request);
        if (customer == null) {
            String answer = "没有找到对应客户，请提供客户名称或 customerId。";
            run.setStatus("COMPLETED");
            run.setAgentOutput(answer);
            return finalAnswer(session.getId(), run.getId(), answer, List.of());
        }
        AgentToolCall profileCall = recordTool(run.getId(), "queryCustomerProfile", Map.of("customerId", customer.getId()), customer, "SUCCESS", null);
        List<ContactLog> logs = contactLogService.listByCustomerId(customer.getId());
        AgentToolCall historyCall = recordTool(run.getId(), "queryContactHistory", Map.of("customerId", customer.getId()), logs, "SUCCESS", null);
        KnowledgeAnswer knowledge = ragService.ask(customer.getIndustry() + " 续费 价格异议 跟进策略", 3);
        AgentToolCall knowledgeCall = recordTool(run.getId(), "searchKnowledge", Map.of("query", customer.getIndustry() + " 续费 价格异议 跟进策略"), knowledge, "SUCCESS", null);

        String answer = "客户现状：" + customer.getName() + " 属于 " + customer.getValueLevel()
                + " 类客户，风险等级 " + customer.getRiskLevel()
                + "。主要动作：复盘历史跟进和曝光效果，围绕客户关注点给出续费/优化建议。建议话术：先确认经营目标，再用数据和同行案例说明方案价值。";
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer);
        return finalAnswer(session.getId(), run.getId(), answer, List.of(view(profileCall), view(historyCall), view(knowledgeCall)));
    }

    private AgentChatResponse proposeTask(AgentChatRequest request, AgentSession session, AgentRun run) {
        Customer customer = resolveCustomer(request);
        if (customer == null) {
            customer = customerService.getById(1001L);
        }
        Lead lead = leadService.list(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getCustomerId, customer.getId())
                .orderByDesc(Lead::getScore)
                .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
        LocalDateTime dueTime = LocalDateTime.now().plusDays(1).with(LocalTime.of(10, 0));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", customer.getId());
        payload.put("leadId", lead == null ? null : lead.getId());
        payload.put("salesRepId", defaultSalesRepId(request.salesRepId()));
        payload.put("title", "跟进" + customer.getName() + "续费意向");
        payload.put("content", "围绕套餐到期、曝光效果、价格异议和下一步复盘沟通。");
        payload.put("dueTime", dueTime.toString());
        payload.put("idempotencyKey", "agent-task-" + run.getId() + "-" + customer.getId());

        AgentToolCall call = recordTool(run.getId(), "createFollowupTask", payload, Map.of("status", "NEED_CONFIRMATION"), "NEED_CONFIRMATION", null);
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

    private AgentChatResponse answerKnowledge(AgentChatRequest request, AgentSession session, AgentRun run) {
        KnowledgeAnswer answer = ragService.ask(request.message(), 5);
        AgentToolCall call = recordTool(run.getId(), "searchKnowledge", Map.of("query", request.message()), answer, "SUCCESS", null);
        run.setStatus("COMPLETED");
        run.setAgentOutput(answer.answer());
        return finalAnswer(session.getId(), run.getId(), answer.answer(), List.of(view(call)));
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
            return Map.of("status", "UNSUPPORTED_ACTION");
        }
        try {
            JsonNode payload = objectMapper.readTree(confirmation.getPayloadJson());
            String idempotencyKey = payload.get("idempotencyKey").asText();
            CrmTask existing = taskService.getOne(new LambdaQueryWrapper<CrmTask>()
                    .eq(CrmTask::getIdempotencyKey, idempotencyKey), false);
            if (existing != null) {
                return existing;
            }
            CrmTask task = new CrmTask();
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
            ContactLog log = new ContactLog();
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
            contactLogService.save(log);
            return log;
        } catch (JsonProcessingException ex) {
            return Map.of("status", "PAYLOAD_ERROR", "message", ex.getMessage());
        }
    }

    private AgentSession getOrCreateSession(AgentChatRequest request) {
        if (request.sessionId() != null) {
            AgentSession existing = sessionService.getById(request.sessionId());
            if (existing != null) {
                return existing;
            }
        }
        AgentSession session = new AgentSession();
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

    private AgentToolCall recordTool(Long runId, String toolName, Object input, Object output, String status, String errorMessage) {
        Instant startedAt = Instant.now();
        AgentToolDefinition definition = toolRegistry.find(toolName)
                .orElse(new AgentToolDefinition(toolName, "unknown", ToolType.READ, false, List.of()));
        AgentToolCall call = new AgentToolCall();
        call.setRunId(runId);
        call.setToolName(toolName);
        call.setToolType(definition.type().name());
        call.setInputJson(toJson(input));
        call.setOutputJson(toJson(output));
        call.setStatus(status);
        call.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
        call.setErrorMessage(errorMessage);
        call.setRequiresConfirmation(definition.requiresConfirmation());
        call.setCompletedAt(LocalDateTime.now());
        toolCallService.save(call);
        eventPublisher.publishToolCallRecorded(call);
        return call;
    }

    private String routeIntent(String message) {
        if (message.contains("创建") && message.contains("任务")) {
            return "CREATE_TASK";
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

    private Customer resolveCustomer(AgentChatRequest request) {
        if (request.customerId() != null) {
            return customerService.getById(request.customerId());
        }
        String message = request.message();
        return customerService.list()
                .stream()
                .filter(customer -> message.contains(customer.getName()))
                .findFirst()
                .orElse(null);
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
