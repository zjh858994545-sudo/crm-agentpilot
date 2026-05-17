package com.agentpilot.callcenter.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CallEndedEventRequest(
        @NotBlank String callId,
        @NotNull Long customerId,
        Long salesRepId,
        Long leadId,
        String recordingUrl,
        @NotBlank String transcript
) {
    public CallTextRequest toCallTextRequest() {
        return new CallTextRequest(customerId, salesRepId, leadId, transcript);
    }
}
