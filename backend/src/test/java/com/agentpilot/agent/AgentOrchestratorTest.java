package com.agentpilot.agent;

import com.agentpilot.agent.tool.ToolRegistry;
import com.agentpilot.events.entity.OutboxEvent;
import com.agentpilot.events.service.OutboxEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Autowired
    private OutboxEventService outboxEventService;

    @Test
    void toolRegistrySeparatesReadAndWriteTools() {
        assertThat(toolRegistry.find("rankLeads")).isPresent();
        assertThat(toolRegistry.find("rankLeads").orElseThrow().requiresConfirmation()).isFalse();
        assertThat(toolRegistry.find("createFollowupTask")).isPresent();
        assertThat(toolRegistry.find("createFollowupTask").orElseThrow().requiresConfirmation()).isTrue();
        assertThat(toolRegistry.find("createFollowupTask").orElseThrow().parametersSchema())
                .containsKeys("type", "properties", "required");
        assertThat(toolRegistry.openAiTools()).isNotEmpty();
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

        mockMvc.perform(get("/api/agent/runs/" + runId + "/execution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routingMode", is("稳定规则处理")))
                .andExpect(jsonPath("$.data.currentStage", is("COMPLETED")))
                .andExpect(jsonPath("$.data.steps.length()", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.steps[0].category", is("REQUEST")));
    }

    @Test
    void agentChatRejectsRequestsOutsideCurrentSalesRepScope() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":2,\"message\":\"今天我应该优先跟进哪些客户？\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":1,\"customerId\":1002,\"message\":\"帮我分析快聘人力\"}"))
                .andExpect(status().isForbidden());
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

        mockMvc.perform(post("/api/agent/confirmations/" + confirmationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        assertThat(outboxEventService.count(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventType, "crm_task.created"))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void agentRunAndConfirmationQueriesAreScopedToCurrentUser() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"salesRepId\":1,\"message\":\"帮我创建明天上午十点跟进美家房产续费的任务\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type", is("confirmation_required")));

        mockMvc.perform(get("/api/agent/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].userId", is(1)))
                .andExpect(jsonPath("$.data[0].salesRepId", is(1)));

        mockMvc.perform(get("/api/agent/confirmations").param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/agent/runs/page")
                        .param("page", "1")
                        .param("pageSize", "5")
                        .param("status", "ALL")
                        .param("keyword", "美家"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[0].userId", is(1)))
                .andExpect(jsonPath("$.data.items[0].salesRepId", is(1)));

        mockMvc.perform(get("/api/agent/confirmations/page")
                        .param("page", "1")
                        .param("pageSize", "5")
                        .param("status", "ALL")
                        .param("keyword", "美家"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void confirmationDecisionRequiresUserId() throws Exception {
        mockMvc.perform(post("/api/agent/confirmations/1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }
}
