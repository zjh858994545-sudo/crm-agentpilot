package com.agentpilot.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI crmAgentPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CRM-AgentPilot API")
                        .version("0.1.0")
                        .description("Interview-ready CRM AI Agent APIs: CRM, RAG, Agent tools, confirmation, call center, and evaluation.")
                        .license(new License().name("Interview Project")));
    }
}
