package com.agentpilot.callcenter.vo;

import jakarta.validation.constraints.NotBlank;

public record CallTextRequest(
        Long customerId,
        Long salesRepId,
        Long leadId,
        @NotBlank String text
) {
}

