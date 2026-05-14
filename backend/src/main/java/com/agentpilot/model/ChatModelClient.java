package com.agentpilot.model;

import java.util.Optional;
import java.util.List;
import java.util.Map;

public interface ChatModelClient {
    Optional<String> complete(String systemPrompt, String userPrompt);

    Optional<ModelToolCall> chooseTool(String systemPrompt, String userPrompt, List<Map<String, Object>> tools);

    String provider();

    String modelName();

    boolean configured();
}
