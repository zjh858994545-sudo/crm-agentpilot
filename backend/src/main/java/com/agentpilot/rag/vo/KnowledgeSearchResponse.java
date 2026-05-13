package com.agentpilot.rag.vo;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        String rewrittenQuery,
        List<KnowledgeItem> items
) {
}

