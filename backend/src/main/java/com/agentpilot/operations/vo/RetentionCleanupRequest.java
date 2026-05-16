package com.agentpilot.operations.vo;

public record RetentionCleanupRequest(Boolean dryRun) {
    public boolean dryRunValue() {
        return dryRun == null || dryRun;
    }
}
