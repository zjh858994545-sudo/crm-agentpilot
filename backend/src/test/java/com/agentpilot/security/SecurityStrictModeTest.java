package com.agentpilot.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void strictModeProtectsOperationalMetricsButAllowsHealthProbe() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics")
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

        mockMvc.perform(get("/api/customers").param("salesRepId", "2")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].ownerSalesRepId", is(2)));

        mockMvc.perform(get("/api/customers/1002")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerSalesRepId", is(2)));

        mockMvc.perform(get("/api/leads/3002")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesRepId", is(2)));
    }

    @Test
    void salesManagerCanRunAgentForTeamMemberButSalesCanOnlySeeOwnRuns() throws Exception {
        String marker = "TEAM_SCOPE_MARKER";
        String message = "\\u4eca\\u5929\\u6211\\u5e94\\u8be5\\u4f18\\u5148\\u8ddf\\u8fdb\\u54ea\\u4e9b\\u5ba2\\u6237\\uff1f" + marker;
        mockMvc.perform(post("/api/agent/chat")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":100,\"salesRepId\":2,\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.type", is("final_answer")));

        mockMvc.perform(get("/api/agent/runs/page")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .param("status", "ALL")
                        .param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.items[0].userId", is(100)))
                .andExpect(jsonPath("$.data.items[0].salesRepId", is(2)));

        mockMvc.perform(get("/api/agent/runs/page")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1")
                        .param("status", "ALL")
                        .param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(0)));

        mockMvc.perform(post("/api/agent/chat")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":2,\"message\":\"" + message + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesManagerCanUseCallCenterForTeamMemberButSalesCannotCrossScope() throws Exception {
        String callText = "\\u5ba2\\u6237\\u8981\\u6c42\\u53d1\\u9001\\u5957\\u9910\\u5bf9\\u6bd4\\u548c\\u4f18\\u60e0\\u5ba1\\u6279\\u65b9\\u6848";
        mockMvc.perform(post("/api/callcenter/contact-log-confirmations")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1002,\"salesRepId\":2,\"leadId\":3002,\"text\":\"" + callText + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload.customerId", is(1002)))
                .andExpect(jsonPath("$.data.payload.salesRepId", is(2)))
                .andExpect(jsonPath("$.data.payload.leadId", is(3002)));

        mockMvc.perform(get("/api/callcenter/customers/1002/memory")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(post("/api/callcenter/contact-log-confirmations")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":1002,\"salesRepId\":2,\"leadId\":3002,\"text\":\"" + callText + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/callcenter/customers/1002/memory")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentRunAuditExportRequiresOperationalPermission() throws Exception {
        mockMvc.perform(get("/api/agent/runs/export")
                        .header("X-AgentPilot-Token", "agentpilot-sales-1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/agent/runs/export")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk());
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

    @Test
    void knowledgeSearchIsScopedToCurrentTenant() throws Exception {
        jdbcTemplate.update("""
                        INSERT INTO crm_knowledge_doc
                        (id, tenant_id, title, doc_type, source, content_hash, status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                99001L, "tenant-other", "Other tenant playbook", "PRIVATE", "test", "tenant-other-doc", "ACTIVE", 900L);
        jdbcTemplate.update("""
                        INSERT INTO crm_knowledge_chunk
                        (id, doc_id, chunk_index, title, content, token_count, keywords, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                99002L, 99001L, 0, "Private chunk", "TENANT_SECRET_ALPHA should never appear in demo tenant search",
                12, "TENANT_SECRET_ALPHA", "");

        MvcResult docsResult = mockMvc.perform(get("/api/knowledge/docs")
                        .header("X-AgentPilot-Token", "agentpilot-manager"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(docsResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("Other tenant playbook");

        MvcResult searchResult = mockMvc.perform(post("/api/knowledge/search")
                        .header("X-AgentPilot-Token", "agentpilot-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"TENANT_SECRET_ALPHA\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(searchResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("Private chunk")
                .doesNotContain("tenant-other");
    }
}
