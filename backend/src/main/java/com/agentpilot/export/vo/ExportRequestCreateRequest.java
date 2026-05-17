package com.agentpilot.export.vo;

public record ExportRequestCreateRequest(
        String exportType,
        String reason
) {
}
