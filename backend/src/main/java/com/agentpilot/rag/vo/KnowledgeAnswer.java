package com.agentpilot.rag.vo;

import java.util.List;

public record KnowledgeAnswer(
        String question,
        String rewrittenQuery,
        String answer,
        boolean refused,
        List<AnswerCitation> citations
) {
}

