package com.agentpilot;

import com.agentpilot.common.config.ApplicationInfoProperties;
import com.agentpilot.callcenter.config.CallCenterWebhookProperties;
import com.agentpilot.callcenter.config.CallProviderProperties;
import com.agentpilot.events.config.EventProperties;
import com.agentpilot.model.config.ModelProperties;
import com.agentpilot.notification.config.NotificationProperties;
import com.agentpilot.operations.config.RetentionProperties;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import com.agentpilot.security.config.JwtSsoProperties;
import com.agentpilot.security.ratelimit.ApiRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ApplicationInfoProperties.class,
        ModelProperties.class,
        CallCenterWebhookProperties.class,
        CallProviderProperties.class,
        NotificationProperties.class,
        EventProperties.class,
        RetentionProperties.class,
        AgentPilotSecurityProperties.class,
        JwtSsoProperties.class,
        ApiRateLimitProperties.class
})
public class AgentPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPilotApplication.class, args);
    }
}
