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
        "agentpilot.rate-limit.default-capacity=2",
        "agentpilot.rate-limit.default-refill-per-minute=1"
})
class ApiRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiRequestsAreRateLimitedPerPrincipal() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("RATE_LIMITED")));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
