package com.agentpilot.agent;

import com.agentpilot.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentOrchestratorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void toolRegistrySeparatesReadAndWriteTools() {
        assertThat(toolRegistry.find("rankLeads")).isPresent();
        assertThat(toolRegistry.find("rankLeads").orElseThrow().requiresConfirmation()).isFalse();
        assertThat(toolRegistry.find("createFollowupTask")).isPresent();
        assertThat(toolRegistry.find("createFollowupTask").orElseThrow().requiresConfirmation()).isTrue();
    }

    @Test
    void agentCanRecommendLeadsAndRecordToolCall() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":1,\"message\":\"今天我应该优先跟进哪些客户？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type", is("final_answer")))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName", is("rankLeads")))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long runId = root.at("/data/runId").asLong();
        mockMvc.perform(get("/api/agent/runs/" + runId + "/tool-calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void writeToolRequiresConfirmationBeforeCreatingTask() throws Exception {
        MvcResult chatResult = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":1,\"message\":\"帮我创建明天上午十点跟进美家房产续费的任务\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type", is("confirmation_required")))
                .andExpect(jsonPath("$.data.toolCalls[0].requiresConfirmation", is(true)))
                .andReturn();

        JsonNode root = objectMapper.readTree(chatResult.getResponse().getContentAsString());
        long confirmationId = root.at("/data/confirmationId").asLong();

        mockMvc.perform(post("/api/agent/confirmations/" + confirmationId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.result.source", is("AGENT")));

        mockMvc.perform(post("/api/agent/confirmations/" + confirmationId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }
}

