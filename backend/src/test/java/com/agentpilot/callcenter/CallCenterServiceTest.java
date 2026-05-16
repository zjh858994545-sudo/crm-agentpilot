package com.agentpilot.callcenter;

import com.agentpilot.callcenter.service.CallCenterService;
import com.agentpilot.callcenter.vo.CallTextRequest;
import com.agentpilot.callcenter.vo.QualityCheckResponse;
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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallCenterServiceTest {

    @Autowired
    private CallCenterService callCenterService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void summaryAndQualityCheckAreStructured() {
        CallTextRequest request = new CallTextRequest(
                1001L,
                1L,
                3001L,
                "客户说套餐有点贵，担心续费后没有效果。销售表示明天上午提供上月曝光数据和同行案例。"
        );

        assertThat(callCenterService.summarize(request).objections()).contains("价格异议", "效果担忧");

        QualityCheckResponse response = callCenterService.qualityCheck(new CallTextRequest(
                1001L,
                1L,
                3001L,
                "销售承诺一定成交并保证收益。"
        ));
        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.violations()).isNotEmpty();
        assertThat(response.citations()).isNotEmpty();

        QualityCheckResponse safeResponse = callCenterService.qualityCheck(new CallTextRequest(
                1001L,
                1L,
                3001L,
                "销售说明不会承诺一定成交，只会提供曝光数据和复盘建议。"
        ));
        assertThat(safeResponse.riskLevel()).isEqualTo("LOW");
        assertThat(safeResponse.violations()).isEmpty();
    }

    @Test
    void contactLogWriteRequiresConfirmation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/callcenter/contact-log-confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1001,"salesRepId":1,"leadId":3001,
                                "text":"客户说套餐有点贵，担心续费后没有效果。销售表示明天提供曝光数据。"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmationId").isNumber())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        long confirmationId = root.at("/data/confirmationId").asLong();

        MvcResult firstConfirm = mockMvc.perform(post("/api/agent/confirmations/" + confirmationId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.result.channel", is("PHONE")))
                .andReturn();

        long firstContactLogId = objectMapper.readTree(firstConfirm.getResponse().getContentAsString())
                .at("/data/result/id")
                .asLong();

        MvcResult duplicate = mockMvc.perform(post("/api/callcenter/contact-log-confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1001,"salesRepId":1,"leadId":3001,
                                "text":"客户说套餐有点贵，担心续费后没有效果。销售表示明天提供曝光数据。"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long duplicateConfirmationId = objectMapper.readTree(duplicate.getResponse().getContentAsString())
                .at("/data/confirmationId")
                .asLong();

        mockMvc.perform(post("/api/agent/confirmations/" + duplicateConfirmationId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.result.id", is((int) firstContactLogId)));
    }

    @Test
    void callCenterRejectsRequestsOutsideCurrentSalesRepScope() throws Exception {
        mockMvc.perform(post("/api/callcenter/contact-log-confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1002,"salesRepId":2,"leadId":3002,
                                "text":"客户要求发送套餐对比。"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/callcenter/customers/1002/memory"))
                .andExpect(status().isForbidden());
    }
}
