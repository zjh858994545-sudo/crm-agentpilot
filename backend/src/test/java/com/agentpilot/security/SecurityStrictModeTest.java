package com.agentpilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "agentpilot.security.mode=strict",
        "agentpilot.security.api-token=test-agentpilot-token"
})
class SecurityStrictModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void strictModeRequiresAgentPilotTokenForApi() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));

        mockMvc.perform(get("/api/customers")
                .header("X-AgentPilot-Token", "test-agentpilot-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void strictModeCanAuthenticateDatabaseBackedRbacUser() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .header("X-AgentPilot-Token", "agentpilot-sales-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].ownerSalesRepId", is(2)));

        mockMvc.perform(get("/api/customers/1001")
                        .header("X-AgentPilot-Token", "agentpilot-sales-2"))
                .andExpect(status().isForbidden());
    }
}
