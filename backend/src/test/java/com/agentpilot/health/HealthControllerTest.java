package com.agentpilot.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "agentpilot.app.model-provider=mock",
        "agentpilot.model.provider=mock",
        "agentpilot.model.embedding.provider=mock"
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsApplicationStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(jsonPath("$.data.app", is("CRM-AgentPilot")))
                .andExpect(jsonPath("$.data.modelProvider", is("mock")));
    }

    @Test
    void modelStatusIncludesEmbeddingProvider() throws Exception {
        mockMvc.perform(get("/api/model/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embedding.provider", is("mock")))
                .andExpect(jsonPath("$.data.embedding.model", is("deterministic-mock")))
                .andExpect(jsonPath("$.data.embedding.dimension", is(16)));

        mockMvc.perform(post("/api/model/embedding")
                        .contentType("application/json")
                        .content("{\"text\":\"客户嫌套餐贵\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vectorLength", is(16)));
    }
}
