package com.agentpilot.common.response;

import org.slf4j.MDC;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
    OffsetDateTime timestamp,
    String traceId
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "success", data, OffsetDateTime.now(), currentTraceId());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, OffsetDateTime.now(), currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get("traceId");
    }
}
