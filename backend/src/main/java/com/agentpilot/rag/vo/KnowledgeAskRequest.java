package com.agentpilot.rag.vo;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeAskRequest(
        @NotBlank String question,
        Integer topK
) {
}

