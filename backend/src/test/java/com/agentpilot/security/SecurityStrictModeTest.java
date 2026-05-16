package com.agentpilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private RbacPrincipalService rbacPrincipalService;

    @Test
    void strictModeRequiresAgentPilotTokenForApi() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/customers")
                .header("X-AgentPilot-Token", "test-agentpilot-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/v3/api-docs")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk());
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

    @Test
    void strictModeCanAuthenticateSalesManagerRole() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.salesRepId", is(1)));
    }

    @Test
    void currentUserEndpointReturnsDatabaseBackedProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .header("X-Forwarded-For", "203.0.113.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(100)))
                .andExpect(jsonPath("$.data.displayName", is("陈明")))
                .andExpect(jsonPath("$.data.salesRepId", is(1)))
                .andExpect(jsonPath("$.data.primaryRole", is("manager")));

        RbacPrincipalService.UserProfile profile = rbacPrincipalService.findProfileByUserId(100L).orElseThrow();
        assertThat(profile.lastAuthenticatedAt()).isNotNull();
        assertThat(profile.lastAuthenticatedIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void securityUsersEndpointRequiresAdminReadablePermission() throws Exception {
        mockMvc.perform(get("/api/security/users")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security/users")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].username", is("linxiaofeng")));
    }

    @Test
    void modelDiagnosticsRequireOperationalPermission() throws Exception {
        mockMvc.perform(post("/api/model/chat")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"ping\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/model/chat")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
