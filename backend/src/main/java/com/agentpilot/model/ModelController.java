package com.agentpilot.model;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.model.config.ModelProperties;
import com.agentpilot.rag.embedding.EmbeddingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/model")
@PreAuthorize("hasAuthority('agent:use')")
public class ModelController {
    private final ChatModelClient chatModelClient;
    private final EmbeddingService embeddingService;
    private final ModelProperties modelProperties;

    public ModelController(ChatModelClient chatModelClient,
                           EmbeddingService embeddingService,
                           ModelProperties modelProperties) {
        this.chatModelClient = chatModelClient;
        this.embeddingService = embeddingService;
        this.modelProperties = modelProperties;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        String chatVendor = vendorLabel(modelProperties.getOpenaiCompatible().getBaseUrl(), chatModelClient.provider());
        String embeddingVendor = vendorLabel(
                modelProperties.getEmbedding().getOpenaiCompatible().getBaseUrl(),
                embeddingService.provider()
        );
        return ApiResponse.ok(Map.of(
                "provider", chatModelClient.provider(),
                "vendor", chatVendor,
                "protocol", protocolLabel(chatModelClient.provider()),
                "model", chatModelClient.modelName(),
                "configured", chatModelClient.configured(),
                "mode", chatModelClient.configured() ? "llm-enabled" : "deterministic-mock",
                "embedding", Map.of(
                        "provider", embeddingService.provider(),
                        "vendor", embeddingVendor,
                        "protocol", protocolLabel(embeddingService.provider()),
                        "model", embeddingService.modelName(),
                        "configured", embeddingService.configured(),
                        "dimension", embeddingService.dimension(),
                        "mode", embeddingService.configured() ? "real-embedding" : "deterministic-mock"
                )
        ));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String systemPrompt = request.getOrDefault("systemPrompt", "You are a concise CRM AI assistant.");
        String userPrompt = request.getOrDefault("prompt", "");
        Optional<String> answer = chatModelClient.complete(systemPrompt, userPrompt);
        return ApiResponse.ok(Map.of(
                "provider", chatModelClient.provider(),
                "model", chatModelClient.modelName(),
                "configured", chatModelClient.configured(),
                "answer", answer.orElse("Model provider is not configured; running deterministic mock mode.")
        ));
    }

    @PostMapping("/embedding")
    public ApiResponse<Map<String, Object>> embedding(@RequestBody Map<String, String> request) {
        String text = request.getOrDefault("text", "");
        double[] vector = embeddingService.embed(text);
        return ApiResponse.ok(Map.of(
                "provider", embeddingService.provider(),
                "model", embeddingService.modelName(),
                "configured", embeddingService.configured(),
                "dimension", embeddingService.dimension(),
                "vectorLength", vector.length
        ));
    }

    private String vendorLabel(String baseUrl, String provider) {
        if (!"openai-compatible".equalsIgnoreCase(provider)) {
            return "deterministic mock";
        }
        if (StringUtils.hasText(baseUrl) && baseUrl.contains("dashscope.aliyuncs.com")) {
            return "阿里云百炼";
        }
        return "OpenAI-compatible provider";
    }

    private String protocolLabel(String provider) {
        return "openai-compatible".equalsIgnoreCase(provider) ? "OpenAI-compatible" : "mock";
    }
}
