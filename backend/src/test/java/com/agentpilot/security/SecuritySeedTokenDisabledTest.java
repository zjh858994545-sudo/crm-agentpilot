package com.agentpilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "agentpilot.security.mode=strict",
        "agentpilot.security.api-token=ops-break-glass-token",
        "agentpilot.security.seed-users-enabled=false"
})
class SecuritySeedTokenDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedRbacTokensCanBeDisabledForDeployment() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header("X-AgentPilot-Token", "ops-break-glass-token"))
                .andExpect(status().isOk());
    }
}
