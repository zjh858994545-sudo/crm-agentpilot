package com.agentpilot.callcenter.vo;

public record CallEndedEventResponse(
        String callId,
        String recordingUrl,
        CallSummaryResponse summary,
        QualityCheckResponse quality,
        ContactLogConfirmationResponse confirmation
) {
}
