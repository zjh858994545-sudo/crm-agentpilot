package com.agentpilot.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolRegistry {
    private final Map<String, AgentToolDefinition> definitions = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new AgentToolDefinition("queryCustomerProfile", "查询客户 360 信息", ToolType.READ, false, List.of("crm:read")));
        register(new AgentToolDefinition("queryContactHistory", "查询客户历史跟进记录", ToolType.READ, false, List.of("crm:read")));
        register(new AgentToolDefinition("rankLeads", "推荐今日优先跟进商机", ToolType.READ, false, List.of("crm:read")));
        register(new AgentToolDefinition("searchKnowledge", "检索销售 SOP、套餐政策和质检规则", ToolType.READ, false, List.of("knowledge:read")));
        register(new AgentToolDefinition("queryProductPackage", "查询产品套餐政策", ToolType.READ, false, List.of("product:read")));
        register(new AgentToolDefinition("createFollowupTask", "创建 CRM 跟进任务", ToolType.WRITE, true, List.of("crm:write")));
        register(new AgentToolDefinition("updateLeadStage", "更新商机阶段", ToolType.WRITE, true, List.of("crm:write")));
        register(new AgentToolDefinition("writeContactLog", "写入客户跟进记录", ToolType.WRITE, true, List.of("crm:write")));
    }

    public List<AgentToolDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public Optional<AgentToolDefinition> find(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    private void register(AgentToolDefinition definition) {
        definitions.put(definition.name(), definition);
    }
}

