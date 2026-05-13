package com.agentpilot.callcenter.vo;

import com.agentpilot.rag.vo.AnswerCitation;

import java.util.List;

public record QualityCheckResponse(
        String riskLevel,
        List<QualityViolation> violations,
        List<AnswerCitation> citations
) {
}

