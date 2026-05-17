package com.agentpilot.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.retention")
public class RetentionProperties {
    private boolean enabled = false;
    private boolean scheduledCleanupEnabled = false;
    private String cleanupCron = "0 30 3 * * *";
    private int agentAuditDays = 180;
    private int retrievalLogDays = 90;
    private int outboxPublishedDays = 30;
    private int exportArtifactDays = 3;
    private int maxDeleteRowsPerRun = 10000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isScheduledCleanupEnabled() {
        return scheduledCleanupEnabled;
    }

    public void setScheduledCleanupEnabled(boolean scheduledCleanupEnabled) {
        this.scheduledCleanupEnabled = scheduledCleanupEnabled;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public int getAgentAuditDays() {
        return agentAuditDays;
    }

    public void setAgentAuditDays(int agentAuditDays) {
        this.agentAuditDays = agentAuditDays;
    }

    public int getRetrievalLogDays() {
        return retrievalLogDays;
    }

    public void setRetrievalLogDays(int retrievalLogDays) {
        this.retrievalLogDays = retrievalLogDays;
    }

    public int getOutboxPublishedDays() {
        return outboxPublishedDays;
    }

    public void setOutboxPublishedDays(int outboxPublishedDays) {
        this.outboxPublishedDays = outboxPublishedDays;
    }

    public int getExportArtifactDays() {
        return exportArtifactDays;
    }

    public void setExportArtifactDays(int exportArtifactDays) {
        this.exportArtifactDays = exportArtifactDays;
    }

    public int getMaxDeleteRowsPerRun() {
        return maxDeleteRowsPerRun;
    }

    public void setMaxDeleteRowsPerRun(int maxDeleteRowsPerRun) {
        this.maxDeleteRowsPerRun = maxDeleteRowsPerRun;
    }
}
