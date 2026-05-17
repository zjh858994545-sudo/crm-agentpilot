package com.agentpilot.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrmCoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .andExpect(jsonPath("$.data.valueLevel", is("A")))
                .andExpect(jsonPath("$.data.contactMobile", is("139****1001")));

        mockMvc.perform(get("/api/customers/1002"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/customers").param("salesRepId", "2"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/customers/page")
                        .param("page", "1")
                        .param("pageSize", "2")
                        .param("keyword", "美家"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[0].ownerSalesRepId", is(1)));

        mockMvc.perform(get("/api/customers/page").param("salesRepId", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void contactLogsCanBeFetchedByCustomer() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO crm_contact_log (
                    id, tenant_id, customer_id, sales_rep_id, lead_id, channel, content,
                    summary, customer_intent, objections, next_action, idempotency_key, contact_at
                ) VALUES (
                    9902, 'demo', 1001, 1, 3001, 'PHONE',
                    '客户补充手机号 13988881234，邮箱 buyer@example.com，希望明天联系。',
                    '需要回拨 13988881234 并发送邮件到 buyer@example.com。',
                    'HIGH', '担心服务效果', '明天回拨 13988881234',
                    'test-contact-log-pii', '2026-06-01 10:00:00'
                )
                """);

        mockMvc.perform(get("/api/customers/1001/contact-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[0].customerId", is(1001)))
                .andExpect(jsonPath("$.data[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].content", is("客户补充手机号 139****1234，邮箱 b***@example.com，希望明天联系。")))
                .andExpect(jsonPath("$.data[0].summary", is("需要回拨 139****1234 并发送邮件到 b***@example.com。")));

        mockMvc.perform(get("/api/customers/1002/contact-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadsTasksAndPackagesCanBeListed() throws Exception {
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].salesRepId", is(1)));

        mockMvc.perform(get("/api/leads/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salesRepId", is(1)));

        mockMvc.perform(get("/api/leads/3002"))
                .andExpect(status().isForbidden());

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

    @Test
    void dashboardMetricsAreComputedForCurrentSalesRep() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.salesRepId", is(1)))
                .andExpect(jsonPath("$.data.summary.highLeadCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.summary.highLeadAmount").exists())
                .andExpect(jsonPath("$.data.leadTrend", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.leadTrend[0].date", matchesPattern("\\d{4}-\\d{2}-\\d{2}|unknown")))
                .andExpect(jsonPath("$.data.riskHeatmap.industries", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.riskHeatmap.cells", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void tenantScopedCustomerQueriesDoNotLeakSameSalesRepFromOtherTenant() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO crm_customer (
                    id, tenant_id, name, industry, city, address, contact_name, contact_mobile,
                    lifecycle_stage, value_level, risk_level, owner_sales_rep_id, tags, remark
                ) VALUES (
                    9901, 'other-tenant', 'Other Tenant Customer', '餐饮', '北京', 'other',
                    'Other Contact', '13999999999', 'ACTIVE', 'A', 'HIGH', 1, '续费', 'cross tenant test'
                )
                """);

        mockMvc.perform(get("/api/customers/9901"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/customers/page")
                        .param("keyword", "Other Tenant Customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(0)));
    }
}
