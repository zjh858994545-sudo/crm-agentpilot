package com.agentpilot.rag.vo;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeImportRequest(
        @NotBlank String title,
        @NotBlank String docType,
        String source,
        @NotBlank String content
) {
}

