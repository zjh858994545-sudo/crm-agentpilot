package com.agentpilot.rag.vo;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeSearchRequest(
        @NotBlank String query,
        Integer topK
) {
}

