package com.agentpilot.rag;

import com.agentpilot.rag.service.RagService;
import com.agentpilot.rag.vo.KnowledgeAnswer;
import com.agentpilot.rag.vo.KnowledgeSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HybridRetrieverTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchFindsPriceObjectionKnowledge() {
        KnowledgeSearchResponse response = ragService.search("商户觉得套餐价格贵时应该怎么沟通？", 5);

        assertThat(response.items()).isNotEmpty();
        assertThat(response.items().get(0).docType()).isIn("OBJECTION_HANDLING", "ROI_PLAYBOOK");
        assertThat(response.items().get(0).score()).isGreaterThan(0.22);
    }

    @Test
    void askReturnsCitationsAndRefusesWeakEvidence() {
        KnowledgeAnswer answer = ragService.ask("客户 30 天内套餐到期应该如何跟进？", 5);
        assertThat(answer.refused()).isFalse();
        assertThat(answer.citations()).isNotEmpty();

        KnowledgeAnswer refused = ragService.ask("请解释量子纠缠实验如何搭建？", 3);
        assertThat(refused.refused()).isTrue();
        assertThat(refused.citations()).isEmpty();
    }

    @Test
    void knowledgeEndpointsReturnSearchAndAskResults() throws Exception {
        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"商户价格贵怎么处理\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/knowledge/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"销售通话里能不能保证收益？\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refused", is(false)))
                .andExpect(jsonPath("$.data.citations.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/knowledge/vectors/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.updatedChunks", greaterThanOrEqualTo(0)));
    }
}
