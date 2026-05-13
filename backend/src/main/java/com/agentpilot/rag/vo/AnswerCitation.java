package com.agentpilot.rag.vo;

public record AnswerCitation(
        Long docId,
        Long chunkId,
        String docTitle,
        String quote
) {
}

