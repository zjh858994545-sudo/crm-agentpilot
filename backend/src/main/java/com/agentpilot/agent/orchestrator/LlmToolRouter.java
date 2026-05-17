package com.agentpilot.agent.orchestrator;

import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.model.ChatModelClient;
import com.agentpilot.model.ModelToolCall;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class LlmToolRouter {
    private final ChatModelClient chatModelClient;
    private final ToolRegistry toolRegistry;

    public LlmToolRouter(ChatModelClient chatModelClient, ToolRegistry toolRegistry) {
        this.chatModelClient = chatModelClient;
        this.toolRegistry = toolRegistry;
    }

    public Optional<ModelToolCall> choose(String message) {
        if (!chatModelClient.configured()) {
            return Optional.empty();
        }
        Optional<ModelToolCall> decision = chatModelClient.chooseTool(systemPrompt(), message, toolRegistry.openAiTools());
        if (decision.isEmpty() || toolRegistry.find(decision.get().name()).isEmpty()) {
            return Optional.empty();
        }
        return decision;
    }

    private String systemPrompt() {
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
}
