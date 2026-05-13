package com.agentpilot.rag.vo;

public record KnowledgeItem(
        Long chunkId,
        Long docId,
        String docTitle,
        String docType,
        String chunkTitle,
        String content,
        double score,
        String retriever
) {
}

