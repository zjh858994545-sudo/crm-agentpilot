package com.agentpilot.rag.retriever;

public record VectorSearchRow(
        Long chunkId,
        Long docId,
        String docTitle,
        String docType,
        String chunkTitle,
        String content,
        String keywords,
        double vectorScore
) {
}
