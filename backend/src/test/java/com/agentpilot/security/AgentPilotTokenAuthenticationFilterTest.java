package com.agentpilot.security;

import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPilotTokenAuthenticationFilterTest {

    @Test
    void tokenUsageAuditIsThrottledPerUserAndClientIp() {
        AgentPilotSecurityProperties properties = new AgentPilotSecurityProperties();
        properties.setTokenAuditMinIntervalSeconds(60);
        AgentPilotTokenAuthenticationFilter filter = new AgentPilotTokenAuthenticationFilter(properties, null, null);

        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.7", 1000L)).isTrue();
        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.7", 20_000L)).isFalse();
        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.8", 20_000L)).isTrue();
        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.7", 62_000L)).isTrue();
    }

    @Test
    void tokenUsageAuditCanBeDisabledForTests() {
        AgentPilotSecurityProperties properties = new AgentPilotSecurityProperties();
        properties.setTokenAuditMinIntervalSeconds(0);
        AgentPilotTokenAuthenticationFilter filter = new AgentPilotTokenAuthenticationFilter(properties, null, null);

        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.7", 1000L)).isTrue();
        assertThat(filter.shouldRecordTokenUse(100L, "203.0.113.7", 1001L)).isTrue();
    }
}
