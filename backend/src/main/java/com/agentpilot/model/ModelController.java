package com.agentpilot.model;

import com.agentpilot.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/model")
public class ModelController {
    private final ChatModelClient chatModelClient;

    public ModelController(ChatModelClient chatModelClient) {
        this.chatModelClient = chatModelClient;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "provider", chatModelClient.provider(),
                "model", chatModelClient.modelName(),
                "configured", chatModelClient.configured(),
                "mode", chatModelClient.configured() ? "llm-enabled" : "deterministic-mock"
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
}
