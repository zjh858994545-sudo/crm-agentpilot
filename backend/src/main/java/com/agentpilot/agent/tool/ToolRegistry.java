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
        register(readTool(
                "analyzeCustomer",
                "综合客户 360、历史跟进和知识库生成客户分析与跟进策略",
                List.of("crm:read", "knowledge:read"),
                objectSchema(Map.of(
                        "customerId", numberProperty("CRM 客户 ID，可为空"),
                        "customerName", stringProperty("客户名称，可为空")
                ), List.of())
        ));
        register(readTool(
                "queryCustomerProfile",
                "查询客户 360 信息",
                List.of("crm:read"),
                objectSchema(Map.of("customerId", numberProperty("CRM 客户 ID")), List.of("customerId"))
        ));
        register(readTool(
                "queryContactHistory",
                "查询客户历史跟进记录",
                List.of("crm:read"),
                objectSchema(Map.of("customerId", numberProperty("CRM 客户 ID")), List.of("customerId"))
        ));
        register(readTool(
                "rankLeads",
                "推荐今日优先跟进商机",
                List.of("crm:read"),
                objectSchema(Map.of(
                        "salesRepId", numberProperty("销售 ID"),
                        "topK", integerProperty("返回数量，最大 20")
                ), List.of("salesRepId"))
        ));
        register(readTool(
                "searchKnowledge",
                "检索销售 SOP、套餐政策和质检规则",
                List.of("knowledge:read"),
                objectSchema(Map.of(
                        "query", stringProperty("检索问题或改写后的查询"),
                        "topK", integerProperty("返回分块数量，最大 20")
                ), List.of("query"))
        ));
        register(readTool(
                "queryProductPackage",
                "查询产品套餐政策",
                List.of("product:read"),
                objectSchema(Map.of("industry", stringProperty("行业，例如房产、招聘、餐饮")), List.of())
        ));
        register(writeTool(
                "createFollowupTask",
                "创建 CRM 跟进任务",
                List.of("crm:write"),
                objectSchema(Map.of(
                        "customerId", numberProperty("CRM 客户 ID"),
                        "leadId", numberProperty("商机 ID，可为空"),
                        "salesRepId", numberProperty("销售 ID"),
                        "title", stringProperty("任务标题"),
                        "content", stringProperty("任务内容"),
                        "dueTime", stringProperty("ISO-8601 任务截止时间"),
                        "idempotencyKey", stringProperty("业务幂等键")
                ), List.of("customerId", "salesRepId", "title", "content", "dueTime", "idempotencyKey"))
        ));
        register(writeTool(
                "updateLeadStage",
                "更新商机阶段",
                List.of("crm:write"),
                objectSchema(Map.of(
                        "leadId", numberProperty("商机 ID"),
                        "stage", stringProperty("目标阶段"),
                        "reason", stringProperty("阶段变更原因")
                ), List.of("leadId", "stage", "reason"))
        ));
        register(writeTool(
                "writeContactLog",
                "写入客户跟进记录",
                List.of("crm:write"),
                objectSchema(Map.of(
                        "customerId", numberProperty("CRM 客户 ID"),
                        "salesRepId", numberProperty("销售 ID"),
                        "leadId", numberProperty("商机 ID，可为空"),
                        "channel", stringProperty("沟通渠道"),
                        "content", stringProperty("原始通话或沟通文本"),
                        "summary", stringProperty("结构化摘要"),
                        "customerIntent", stringProperty("客户意向等级"),
                        "objections", stringProperty("客户异议"),
                        "nextAction", stringProperty("下一步动作"),
                        "contactAt", stringProperty("ISO-8601 联系时间")
                ), List.of("customerId", "salesRepId", "channel", "content", "summary", "contactAt"))
        ));
    }

    public List<AgentToolDefinition> list() {
        return List.copyOf(definitions.values());
    }

    public List<Map<String, Object>> openAiTools() {
        return list().stream()
                .map(definition -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", definition.name(),
                                "description", definition.description(),
                                "parameters", definition.parametersSchema()
                        )
                ))
                .toList();
    }

    public Optional<AgentToolDefinition> find(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    private AgentToolDefinition readTool(
            String name,
            String description,
            List<String> permissions,
            Map<String, Object> schema
    ) {
        return new AgentToolDefinition(name, description, ToolType.READ, false, permissions, schema);
    }

    private AgentToolDefinition writeTool(
            String name,
            String description,
            List<String> permissions,
            Map<String, Object> schema
    ) {
        return new AgentToolDefinition(name, description, ToolType.WRITE, true, permissions, schema);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required
        );
    }

    private Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> numberProperty(String description) {
        return Map.of("type", "number", "description", description);
    }

    private Map<String, Object> integerProperty(String description) {
        return Map.of("type", "integer", "description", description, "minimum", 1);
    }

    private void register(AgentToolDefinition definition) {
        definitions.put(definition.name(), definition);
    }
}
