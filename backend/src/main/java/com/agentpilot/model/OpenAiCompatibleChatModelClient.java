package com.agentpilot.model;

import com.agentpilot.model.config.ModelProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatModelClient.class);

    private final ModelProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatModelClient(ModelProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.requestFactory(requestFactory()).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ModelToolCall> chooseTool(String systemPrompt, String userPrompt, List<Map<String, Object>> tools) {
        if (!configured() || !"openai-compatible".equalsIgnoreCase(properties.getProvider()) || tools == null || tools.isEmpty()) {
            return Optional.empty();
        }
        ModelProperties.OpenAiCompatible config = properties.getOpenaiCompatible();
        Map<String, Object> body = Map.of(
                "model", config.getChatModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "tools", tools,
                "tool_choice", "auto"
        );
        try {
            JsonNode response = restClient.post()
                    .uri(chatCompletionsEndpoint(config.getBaseUrl()))
                    .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode toolCall = response == null ? null : response.at("/choices/0/message/tool_calls/0");
            if (toolCall == null || toolCall.isMissingNode() || toolCall.isNull()) {
                return Optional.empty();
            }
            String name = toolCall.at("/function/name").asText("");
            String rawArguments = toolCall.at("/function/arguments").asText("{}");
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }
            return Optional.of(new ModelToolCall(
                    toolCall.path("id").asText(""),
                    name,
                    parseArguments(rawArguments),
                    rawArguments
            ));
        } catch (RuntimeException ex) {
            log.warn("OpenAI-compatible tool selection failed. provider={} model={} message={}",
                    properties.getProvider(), config.getChatModel(), ex.getMessage());
            return Optional.empty();
        }
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
            log.warn("OpenAI-compatible chat completion failed. provider={} model={} message={}",
                    properties.getProvider(), config.getChatModel(), ex.getMessage());
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

    private Map<String, Object> parseArguments(String rawArguments) {
        if (!StringUtils.hasText(rawArguments)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawArguments, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse tool arguments as JSON. arguments={}", rawArguments);
            return Map.of();
        }
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return factory;
    }
}
