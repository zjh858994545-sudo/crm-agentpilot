package com.agentpilot.scoring;

import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeadScoringServiceTest {

    @Autowired
    private LeadScoringService leadScoringService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void recommendReturnsSortedExplainableResults() {
        List<LeadRecommendation> recommendations = leadScoringService.recommend(null, 5);

        assertThat(recommendations).hasSize(5);
        assertThat(recommendations)
                .isSortedAccordingTo((left, right) -> Double.compare(right.score(), left.score()));
        assertThat(recommendations.get(0).reasons()).isNotEmpty();
        assertThat(recommendations.get(0).suggestedAction()).isNotBlank();
        assertThat(recommendations.get(0).priority()).isIn("HIGH", "MEDIUM", "LOW");
    }

    @Test
    void recommendEndpointSupportsSalesRepFilter() throws Exception {
        mockMvc.perform(get("/api/leads/recommend?salesRepId=1&topK=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].reasons").isArray())
                .andExpect(jsonPath("$.data[0].suggestedAction").isString());
    }
}

