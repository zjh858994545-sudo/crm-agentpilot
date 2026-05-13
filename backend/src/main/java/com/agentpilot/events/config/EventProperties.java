package com.agentpilot.events.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.events")
public class EventProperties {
    private boolean kafkaEnabled;
    private String agentRunTopic = "agent-run-events";
    private String agentToolCallTopic = "agent-tool-call-events";
    private String crmTaskTopic = "crm-task-events";

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public String getAgentRunTopic() {
        return agentRunTopic;
    }

    public void setAgentRunTopic(String agentRunTopic) {
        this.agentRunTopic = agentRunTopic;
    }

    public String getAgentToolCallTopic() {
        return agentToolCallTopic;
    }

    public void setAgentToolCallTopic(String agentToolCallTopic) {
        this.agentToolCallTopic = agentToolCallTopic;
    }

    public String getCrmTaskTopic() {
        return crmTaskTopic;
    }

    public void setCrmTaskTopic(String crmTaskTopic) {
        this.crmTaskTopic = crmTaskTopic;
    }
}
