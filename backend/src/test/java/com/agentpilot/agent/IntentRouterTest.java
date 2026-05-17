package com.agentpilot.agent;

import com.agentpilot.agent.orchestrator.IntentRouter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRouterTest {
    private final IntentRouter router = new IntentRouter();

    @Test
    void routesWriteIntentsToDeterministicFlow() {
        String intent = router.route("帮我创建明天上午跟进美家房产的任务");

        assertThat(intent).isEqualTo("CREATE_TASK");
        assertThat(router.requiresDeterministicWriteFlow(intent)).isTrue();
    }

    @Test
    void routesBusinessReadIntents() {
        assertThat(router.route("今天我应该优先跟进谁")).isEqualTo("LEAD_RECOMMENDATION");
        assertThat(router.route("帮我分析美家房产")).isEqualTo("CUSTOMER_ANALYSIS");
        assertThat(router.route("价格政策能不能解释一下")).isEqualTo("KNOWLEDGE_QA");
    }
}
