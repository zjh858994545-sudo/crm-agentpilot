package com.agentpilot.agent.service;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.vo.AgentExecutionStep;
import com.agentpilot.agent.vo.AgentExecutionTrace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class AgentExecutionTraceService {
    private final AgentRunService runService;
    private final AgentToolCallService toolCallService;
    private final AgentConfirmationService confirmationService;

    public AgentExecutionTraceService(
            AgentRunService runService,
            AgentToolCallService toolCallService,
            AgentConfirmationService confirmationService
    ) {
        this.runService = runService;
        this.toolCallService = toolCallService;
        this.confirmationService = confirmationService;
    }

    public AgentExecutionTrace build(Long runId) {
        AgentRun run = runService.getById(runId);
        if (run == null) {
            throw new IllegalArgumentException("Agent run not found: " + runId);
        }
        List<AgentToolCall> toolCalls = toolCallService.listByRunId(runId);
        List<AgentConfirmation> confirmations = confirmationService.listByRunId(runId);
        Map<Long, AgentConfirmation> confirmationByToolCall = confirmations.stream()
                .filter(item -> item.getToolCallId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        AgentConfirmation::getToolCallId,
                        item -> item,
                        (left, right) -> right
                ));

        List<AgentExecutionStep> steps = new ArrayList<>();
        steps.add(new AgentExecutionStep(
                "receive",
                "接收销售问题",
                summarizeUserInput(run.getUserInput()),
                "COMPLETED",
                "REQUEST",
                null,
                null,
                null,
                null,
                null,
                run.getCompletedAt(),
                null,
                null
        ));
        steps.add(new AgentExecutionStep(
                "route",
                "理解问题并选择处理方式",
                routingDescription(run),
                "COMPLETED",
                "ROUTING",
                null,
                null,
                null,
                null,
                null,
                run.getCompletedAt(),
                null,
                null
        ));

        for (AgentToolCall call : toolCalls) {
            AgentConfirmation confirmation = confirmationByToolCall.get(call.getId());
            steps.add(new AgentExecutionStep(
                    "tool-" + call.getId(),
                    toolTitle(call),
                    toolDescription(call, confirmation),
                    stepStatus(call, confirmation),
                    "TOOL",
                    call.getToolName(),
                    call.getToolType(),
                    call.getLatencyMs(),
                    confirmation == null ? call.getConfirmationId() : confirmation.getId(),
                    confirmation == null ? null : confirmation.getStatus(),
                    call.getCompletedAt(),
                    call.getInputJson(),
                    call.getOutputJson()
            ));
        }

        confirmations.stream()
                .filter(item -> item.getToolCallId() == null || !confirmationByToolCall.containsKey(item.getToolCallId()))
                .sorted(Comparator.comparing(AgentConfirmation::getId))
                .forEach(item -> steps.add(confirmationStep(item)));

        steps.add(new AgentExecutionStep(
                "final-output",
                finalStepTitle(run, confirmations),
                finalStepDescription(run, confirmations),
                finalStepStatus(run, confirmations),
                "OUTPUT",
                null,
                null,
                run.getLatencyMs(),
                null,
                null,
                run.getCompletedAt(),
                null,
                null
        ));

        boolean requiresConfirmation = confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PENDING"));
        return new AgentExecutionTrace(
                run,
                toolCalls,
                confirmations,
                steps,
                routingMode(run),
                currentStage(run, confirmations),
                "写 CRM 前必须生成确认单，人确认后才执行真正写入。",
                requiresConfirmation
        );
    }

    private AgentExecutionStep confirmationStep(AgentConfirmation item) {
        return new AgentExecutionStep(
                "confirmation-" + item.getId(),
                "写入确认单",
                item.getActionSummary(),
                confirmationStatus(item.getStatus()),
                "CONFIRMATION",
                item.getActionType(),
                "WRITE",
                null,
                item.getId(),
                item.getStatus(),
                Optional.ofNullable(item.getConfirmedAt()).orElse(item.getExpiredAt()),
                item.getPayloadJson(),
                null
        );
    }

    private String routingMode(AgentRun run) {
        String intent = Optional.ofNullable(run.getIntent()).orElse("");
        if (intent.startsWith("LLM_TOOL:")) {
            return "智能工具选择";
        }
        return "稳定规则处理";
    }

    private String routingDescription(AgentRun run) {
        String intent = Optional.ofNullable(run.getIntent()).orElse("UNKNOWN");
        if (intent.startsWith("LLM_TOOL:")) {
            return "AI 根据问题选择了 " + intent.substring("LLM_TOOL:".length()) + "。";
        }
        return "系统使用稳定规则处理到 " + intent + "。";
    }

    private String currentStage(AgentRun run, List<AgentConfirmation> confirmations) {
        if (confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PENDING"))) {
            return "WAITING_CONFIRMATION";
        }
        if (confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PROCESSING"))) {
            return "WRITING_CRM";
        }
        if (Objects.equals(run.getStatus(), "FAILED")) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    private String toolTitle(AgentToolCall call) {
        return switch (call.getToolName()) {
            case "rankLeads" -> "计算商机优先级";
            case "queryCustomerProfile" -> "读取客户画像";
            case "queryContactHistory" -> "读取跟进历史";
            case "searchKnowledge" -> "检索销售知识库";
            case "queryProductPackage" -> "查询套餐政策";
            case "createFollowupTask" -> "生成跟进任务草稿";
            case "writeContactLog" -> "生成联系记录草稿";
            case "updateLeadStage" -> "生成商机阶段变更草稿";
            default -> call.getToolName();
        };
    }

    private String toolDescription(AgentToolCall call, AgentConfirmation confirmation) {
        if (Boolean.TRUE.equals(call.getRequiresConfirmation())) {
            String status = confirmation == null ? "待生成确认单" : confirmation.getStatus();
            return "写入动作不会直接落库，当前确认状态：" + status + "。";
        }
        return "读取客户事实或知识库，用于生成可追溯回答。";
    }

    private String stepStatus(AgentToolCall call, AgentConfirmation confirmation) {
        if (Objects.equals(call.getStatus(), "FAILED")) {
            return "FAILED";
        }
        if (Boolean.TRUE.equals(call.getRequiresConfirmation())) {
            return confirmation == null ? "WAITING" : confirmationStatus(confirmation.getStatus());
        }
        return "COMPLETED";
    }

    private String confirmationStatus(String status) {
        return switch (Optional.ofNullable(status).orElse("")) {
            case "CONFIRMED" -> "COMPLETED";
            case "REJECTED" -> "REJECTED";
            case "PROCESSING" -> "RUNNING";
            default -> "WAITING";
        };
    }

    private String finalStepTitle(AgentRun run, List<AgentConfirmation> confirmations) {
        if (confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PENDING"))) {
            return "等待人工确认";
        }
        if (Objects.equals(run.getStatus(), "FAILED")) {
            return "执行失败";
        }
        return "完成输出";
    }

    private String finalStepDescription(AgentRun run, List<AgentConfirmation> confirmations) {
        if (confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PENDING"))) {
            return "AI 助手已生成建议，写 CRM 动作需要销售确认。";
        }
        if (Objects.equals(run.getStatus(), "FAILED")) {
            return Optional.ofNullable(run.getErrorMessage()).orElse("执行过程中出现异常。");
        }
        return "回答已生成，审计表已记录本次运行事实。";
    }

    private String finalStepStatus(AgentRun run, List<AgentConfirmation> confirmations) {
        if (confirmations.stream().anyMatch(item -> Objects.equals(item.getStatus(), "PENDING"))) {
            return "WAITING";
        }
        if (Objects.equals(run.getStatus(), "FAILED")) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    private String summarizeUserInput(String input) {
        if (input == null || input.isBlank()) {
            return "空输入";
        }
        return input.length() > 80 ? input.substring(0, 80) + "..." : input;
    }
}
