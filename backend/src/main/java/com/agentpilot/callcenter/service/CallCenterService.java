package com.agentpilot.callcenter.service;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentSession;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.service.AgentConfirmationService;
import com.agentpilot.agent.service.AgentRunService;
import com.agentpilot.agent.service.AgentSessionService;
import com.agentpilot.agent.service.AgentToolCallService;
import com.agentpilot.callcenter.vo.CallSummaryResponse;
import com.agentpilot.callcenter.vo.CallTextRequest;
import com.agentpilot.callcenter.vo.ContactLogConfirmationResponse;
import com.agentpilot.callcenter.vo.QualityCheckResponse;
import com.agentpilot.callcenter.vo.QualityViolation;
import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CallCenterService {
    private final RagService ragService;
    private final AgentSessionService sessionService;
    private final AgentRunService runService;
    private final AgentToolCallService toolCallService;
    private final AgentConfirmationService confirmationService;
    private final ObjectMapper objectMapper;

    public CallCenterService(
            RagService ragService,
            AgentSessionService sessionService,
            AgentRunService runService,
            AgentToolCallService toolCallService,
            AgentConfirmationService confirmationService,
            ObjectMapper objectMapper
    ) {
        this.ragService = ragService;
        this.sessionService = sessionService;
        this.runService = runService;
        this.toolCallService = toolCallService;
        this.confirmationService = confirmationService;
        this.objectMapper = objectMapper;
    }

    public CallSummaryResponse summarize(CallTextRequest request) {
        String text = request.text();
        String intent = text.contains("续费") || text.contains("优惠") ? "MEDIUM" : "UNKNOWN";
        String objections = detectObjections(text);
        String nextAction = text.contains("明天") ? "明天提供数据和案例后继续跟进" : "约定下一次跟进时间并补充证明材料";
        String summary = "客户表达：" + compact(text) + "；销售需围绕客户关注点继续跟进。";
        return new CallSummaryResponse(summary, intent, objections, nextAction);
    }

    public QualityCheckResponse qualityCheck(CallTextRequest request) {
        KnowledgeAnswer evidence = ragService.ask("销售通话质检 违规承诺 保证收益 优惠审批", 3);
        List<QualityViolation> violations = new ArrayList<>();
        String text = removeNegatedCommitments(request.text());
        if (containsAny(text, "保证收益", "保证排名", "一定成交", "保证入职", "保证治疗效果")) {
            violations.add(new QualityViolation(
                    "禁止违规承诺",
                    "HIGH",
                    text,
                    "删除不可控结果承诺，改为说明曝光机会、线索承接和复盘机制"
            ));
        }
        if (text.contains("一定优惠") || text.contains("肯定优惠")) {
            violations.add(new QualityViolation(
                    "优惠表达边界",
                    "MEDIUM",
                    text,
                    "优惠需表述为申请或以审批结果为准"
            ));
        }
        String riskLevel = violations.stream().anyMatch(item -> "HIGH".equals(item.severity()))
                ? "HIGH"
                : (violations.isEmpty() ? "LOW" : "MEDIUM");
        return new QualityCheckResponse(riskLevel, violations, evidence.citations());
    }

    @Transactional
    public ContactLogConfirmationResponse proposeContactLog(CallTextRequest request) {
        CallSummaryResponse summary = summarize(request);
        Long currentUserId = CurrentUser.userId();
        LocalDateTime contactAt = LocalDateTime.now();
        String idempotencyKey = "agent-contact-log-" + request.customerId() + "-"
                + contactAt.toLocalDate() + "-"
                + Integer.toHexString(Objects.hash(request.customerId(), request.leadId(), request.text(), contactAt.toLocalDate()));

        AgentSession session = new AgentSession();
        session.setUserId(currentUserId);
        session.setSalesRepId(request.salesRepId() == null ? 1L : request.salesRepId());
        session.setCustomerId(request.customerId());
        session.setTitle("通话摘要写入确认");
        session.setStatus("ACTIVE");
        sessionService.save(session);

        AgentRun run = new AgentRun();
        run.setSessionId(session.getId());
        run.setUserId(currentUserId);
        run.setSalesRepId(session.getSalesRepId());
        run.setCustomerId(request.customerId());
        run.setUserInput(request.text());
        run.setIntent("WRITE_CONTACT_LOG");
        run.setStatus("NEED_CONFIRMATION");
        run.setModelName("call-summary-rule");
        run.setAgentOutput(summary.summary());
        run.setCompletedAt(LocalDateTime.now());
        runService.save(run);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", request.customerId());
        payload.put("salesRepId", session.getSalesRepId());
        payload.put("leadId", request.leadId());
        payload.put("channel", "PHONE");
        payload.put("content", request.text());
        payload.put("summary", summary.summary());
        payload.put("customerIntent", summary.customerIntent());
        payload.put("objections", summary.objections());
        payload.put("nextAction", summary.nextAction());
        payload.put("contactAt", contactAt.toString());
        payload.put("idempotencyKey", idempotencyKey);

        AgentToolCall toolCall = new AgentToolCall();
        toolCall.setRunId(run.getId());
        toolCall.setToolName("writeContactLog");
        toolCall.setToolType("WRITE");
        toolCall.setInputJson(toJson(payload));
        toolCall.setOutputJson("{\"status\":\"NEED_CONFIRMATION\"}");
        toolCall.setStatus("NEED_CONFIRMATION");
        toolCall.setLatencyMs(0L);
        toolCall.setRequiresConfirmation(true);
        toolCall.setCompletedAt(LocalDateTime.now());
        toolCallService.save(toolCall);

        AgentConfirmation confirmation = new AgentConfirmation();
        confirmation.setRunId(run.getId());
        confirmation.setToolCallId(toolCall.getId());
        confirmation.setActionType("WRITE_CONTACT_LOG");
        confirmation.setActionSummary("写入客户通话摘要到跟进记录");
        confirmation.setPayloadJson(toJson(payload));
        confirmation.setStatus("PENDING");
        confirmation.setExpiredAt(LocalDateTime.now().plusHours(24));
        confirmationService.save(confirmation);

        toolCall.setConfirmationId(confirmation.getId());
        toolCallService.updateById(toolCall);

        return new ContactLogConfirmationResponse(run.getId(), confirmation.getId(), confirmation.getActionSummary(), payload);
    }

    private String detectObjections(String text) {
        List<String> objections = new ArrayList<>();
        if (containsAny(text, "贵", "价格", "预算")) {
            objections.add("价格异议");
        }
        if (containsAny(text, "效果", "没有效果", "担心")) {
            objections.add("效果担忧");
        }
        return objections.isEmpty() ? "" : String.join(",", objections);
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String removeNegatedCommitments(String text) {
        String result = text;
        String[] negations = {"不会承诺", "不承诺", "不能承诺", "不得承诺", "不会", "不能", "不得", "不保证", "不能保证", "禁止"};
        String[] terms = {"一定成交", "保证收益", "保证排名", "保证入职", "保证治疗效果", "一定优惠", "肯定优惠"};
        for (String negation : negations) {
            for (String term : terms) {
                result = result.replace(negation + term, negation + "该结果");
            }
        }
        return result;
    }

    private String compact(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
