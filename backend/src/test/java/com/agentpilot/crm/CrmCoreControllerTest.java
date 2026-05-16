package com.agentpilot.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrmCoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customersCanBeListedAndFetched() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].ownerSalesRepId", is(1)));

        mockMvc.perform(get("/api/customers/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("美家房产")))
                .andExpect(jsonPath("$.data.valueLevel", is("A")));

        mockMvc.perform(get("/api/customers/1002"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/customers").param("salesRepId", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contactLogsCanBeFetchedByCustomer() throws Exception {
        mockMvc.perform(get("/api/customers/1001/contact-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[0].customerId", is(1001)));

        mockMvc.perform(get("/api/customers/1002/contact-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadsTasksAndPackagesCanBeListed() throws Exception {
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].salesRepId", is(1)));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].salesRepId", is(1)));

        mockMvc.perform(get("/api/leads").param("salesRepId", "2"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tasks").param("salesRepId", "2"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/products/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(5))));
    }
}
