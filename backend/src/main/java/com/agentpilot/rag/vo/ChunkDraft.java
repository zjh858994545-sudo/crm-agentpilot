package com.agentpilot.rag.vo;

public record ChunkDraft(
        int index,
        String title,
        String content,
        int tokenCount,
        String keywords
) {
}

