package com.agentpilot.agent.orchestrator;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class IntentRouter {
    private static final Set<String> DETERMINISTIC_WRITE_INTENTS = Set.of(
            "CREATE_TASK",
            "WRITE_CONTACT_LOG",
            "UPDATE_LEAD_STAGE"
    );

    public String route(String message) {
        String text = message == null ? "" : message;
        if (text.contains("创建") && text.contains("任务")) {
            return "CREATE_TASK";
        }
        if ((text.contains("写入") || text.contains("记录")) && text.contains("联系记录")) {
            return "WRITE_CONTACT_LOG";
        }
        if ((text.contains("更新") || text.contains("修改")) && text.contains("商机") && text.contains("阶段")) {
            return "UPDATE_LEAD_STAGE";
        }
        if (text.contains("优先") || text.contains("跟进哪些") || text.contains("跟进谁")) {
            return "LEAD_RECOMMENDATION";
        }
        if (text.contains("分析") || text.contains("客户")) {
            return "CUSTOMER_ANALYSIS";
        }
        if (text.contains("怎么") || text.contains("能不能") || text.contains("政策") || text.contains("质检")) {
            return "KNOWLEDGE_QA";
        }
        return "GENERAL";
    }

    public boolean requiresDeterministicWriteFlow(String intent) {
        return DETERMINISTIC_WRITE_INTENTS.contains(intent);
    }
}
