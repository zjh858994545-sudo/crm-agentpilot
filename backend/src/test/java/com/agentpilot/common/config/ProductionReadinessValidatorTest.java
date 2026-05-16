package com.agentpilot.common.config;

import com.agentpilot.model.config.ModelProperties;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionReadinessValidatorTest {

    @Test
    void productionPhaseRejectsUnsafeDefaults() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                new ApplicationInfoProperties("CRM-AgentPilot", "0.1.0", "production", "mock"),
                new AgentPilotSecurityProperties(),
                new ModelProperties()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENTPILOT_SECURITY_MODE must be strict")
                .hasMessageContaining("AGENTPILOT_SEED_USERS_ENABLED must be false")
                .hasMessageContaining("AGENT_MODEL_PROVIDER must use a real provider")
                .hasMessageContaining("AGENT_EMBEDDING_PROVIDER must use a real provider");
    }

    @Test
    void productionPhaseAllowsStrictRbacAndRealProviders() {
        AgentPilotSecurityProperties security = new AgentPilotSecurityProperties();
        security.setMode("strict");
        security.setSeedUsersEnabled(false);

        ModelProperties model = new ModelProperties();
        model.setProvider("openai-compatible");
        model.getOpenaiCompatible().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        model.getOpenaiCompatible().setApiKey("test-key");
        model.getOpenaiCompatible().setChatModel("qwen3.6-flash");
        model.getEmbedding().setProvider("openai-compatible");
        model.getEmbedding().getOpenaiCompatible().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        model.getEmbedding().getOpenaiCompatible().setApiKey("test-key");
        model.getEmbedding().getOpenaiCompatible().setEmbeddingModel("text-embedding-v4");
        model.getEmbedding().getOpenaiCompatible().setDimensions(1024);

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                new ApplicationInfoProperties("CRM-AgentPilot", "0.1.0", "production", "openai-compatible"),
                security,
                model
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void nonProductionPhaseKeepsLocalDevelopmentFlexible() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                new ApplicationInfoProperties("CRM-AgentPilot", "0.1.0", "ready", "mock"),
                new AgentPilotSecurityProperties(),
                new ModelProperties()
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
