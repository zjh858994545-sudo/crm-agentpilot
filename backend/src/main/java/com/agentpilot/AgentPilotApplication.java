package com.agentpilot;

import com.agentpilot.common.config.ApplicationInfoProperties;
import com.agentpilot.events.config.EventProperties;
import com.agentpilot.model.config.ModelProperties;
import com.agentpilot.security.config.AgentPilotSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ApplicationInfoProperties.class,
        ModelProperties.class,
        EventProperties.class,
        AgentPilotSecurityProperties.class
})
public class AgentPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPilotApplication.class, args);
    }
}
