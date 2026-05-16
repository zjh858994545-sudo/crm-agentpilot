package com.agentpilot.common.config;

import com.agentpilot.model.config.ModelProperties;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ProductionReadinessValidator implements ApplicationRunner {
    private final ApplicationInfoProperties appProperties;
    private final AgentPilotSecurityProperties securityProperties;
    private final ModelProperties modelProperties;

    public ProductionReadinessValidator(
            ApplicationInfoProperties appProperties,
            AgentPilotSecurityProperties securityProperties,
            ModelProperties modelProperties
    ) {
        this.appProperties = appProperties;
        this.securityProperties = securityProperties;
        this.modelProperties = modelProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (!isProductionPhase(appProperties.phase())) {
            return;
        }
        List<String> errors = new ArrayList<>();
        if (!securityProperties.strict()) {
            errors.add("AGENTPILOT_SECURITY_MODE must be strict in production phase");
        }
        if (securityProperties.isSeedUsersEnabled()) {
            errors.add("AGENTPILOT_SEED_USERS_ENABLED must be false in production phase");
        }
        validateChatModel(errors);
        validateEmbeddingModel(errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production readiness check failed: " + String.join("; ", errors));
        }
    }

    private void validateChatModel(List<String> errors) {
        String provider = normalized(modelProperties.getProvider());
        if (provider.isBlank() || "mock".equals(provider)) {
            errors.add("AGENT_MODEL_PROVIDER must use a real provider in production phase");
            return;
        }
        if ("openai-compatible".equals(provider)) {
            ModelProperties.OpenAiCompatible config = modelProperties.getOpenaiCompatible();
            if (!StringUtils.hasText(config.getBaseUrl())) {
                errors.add("OPENAI_COMPATIBLE_BASE_URL is required for production chat model");
            }
            if (!StringUtils.hasText(config.getApiKey())) {
                errors.add("OPENAI_COMPATIBLE_API_KEY is required for production chat model");
            }
            if (!StringUtils.hasText(config.getChatModel())) {
                errors.add("OPENAI_COMPATIBLE_CHAT_MODEL is required for production chat model");
            }
        }
    }

    private void validateEmbeddingModel(List<String> errors) {
        ModelProperties.Embedding embedding = modelProperties.getEmbedding();
        String provider = normalized(embedding.getProvider());
        if (provider.isBlank() || "mock".equals(provider)) {
            errors.add("AGENT_EMBEDDING_PROVIDER must use a real provider in production phase");
            return;
        }
        if ("openai-compatible".equals(provider)) {
            ModelProperties.Embedding.OpenAiCompatible config = embedding.getOpenaiCompatible();
            if (!StringUtils.hasText(config.getBaseUrl())) {
                errors.add("OPENAI_COMPATIBLE_EMBEDDING_BASE_URL is required for production embedding");
            }
            if (!StringUtils.hasText(config.getApiKey())) {
                errors.add("OPENAI_COMPATIBLE_EMBEDDING_API_KEY is required for production embedding");
            }
            if (!StringUtils.hasText(config.getEmbeddingModel())) {
                errors.add("OPENAI_COMPATIBLE_EMBEDDING_MODEL is required for production embedding");
            }
            if (config.getDimensions() <= 0) {
                errors.add("OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS must be positive");
            }
        }
    }

    private boolean isProductionPhase(String phase) {
        return switch (normalized(phase)) {
            case "prod", "production", "commercial", "launch" -> true;
            default -> false;
        };
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
