package com.agentpilot.model;

import java.util.Optional;

public interface ChatModelClient {
    Optional<String> complete(String systemPrompt, String userPrompt);

    String provider();

    String modelName();

    boolean configured();
}
