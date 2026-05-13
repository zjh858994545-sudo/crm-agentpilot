package com.agentpilot.model;

import com.agentpilot.model.config.ModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {
    private final ModelProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleChatModelClient(ModelProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!configured() || !"openai-compatible".equalsIgnoreCase(properties.getProvider())) {
            return Optional.empty();
        }
        ModelProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        Map<String, Object> body = Map.of(
                "model", config.getChatModel(),
                "temperature", config.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        try {
            JsonNode response = restClient.post()
                    .uri(chatCompletionsEndpoint(config.getBaseUrl()))
                    .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return Optional.empty();
            }
            String content = response.at("/choices/0/message/content").asText("");
            return StringUtils.hasText(content) ? Optional.of(content.strip()) : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String provider() {
        return properties.getProvider();
    }

    @Override
    public String modelName() {
        if ("openai-compatible".equalsIgnoreCase(properties.getProvider())) {
            return properties.getOpenaiCompatible().getChatModel();
        }
        return "mock-router";
    }

    @Override
    public boolean configured() {
        ModelProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        return StringUtils.hasText(config.getBaseUrl()) && StringUtils.hasText(config.getApiKey());
    }

    private String chatCompletionsEndpoint(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
