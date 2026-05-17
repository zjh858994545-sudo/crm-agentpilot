package com.agentpilot.dashboard.service;

import com.agentpilot.dashboard.vo.DashboardMetrics;
import com.agentpilot.dashboard.vo.DashboardRiskCell;
import com.agentpilot.dashboard.vo.DashboardRiskHeatmap;
import com.agentpilot.dashboard.vo.DashboardSummary;
import com.agentpilot.dashboard.vo.DashboardTrendPoint;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class DashboardMetricsService {
    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");
    private static final String HIGH_RISK_CUSTOMER_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_customer
            WHERE tenant_id = ?
              AND owner_sales_rep_id = ?
              AND UPPER(COALESCE(risk_level, '')) = 'HIGH'
            """;
    private static final String RENEWAL_CUSTOMER_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_customer
            WHERE tenant_id = ?
              AND owner_sales_rep_id = ?
              AND (
                  COALESCE(lifecycle_stage, '') LIKE ?
                  OR COALESCE(tags, '') LIKE ?
              )
            """;
    private static final String DUE_TASK_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_task
            WHERE tenant_id = ?
              AND sales_rep_id = ?
              AND UPPER(COALESCE(status, '')) IN ('TODO', 'PENDING', 'OPEN')
              AND due_time IS NOT NULL
              AND due_time <= ?
            """;
    private static final String PENDING_CONFIRMATION_COUNT_SQL = """
            SELECT COUNT(*)
            FROM crm_agent_confirmation confirmation
            WHERE confirmation.status = 'PENDING'
              AND EXISTS (
                  SELECT 1
                  FROM crm_agent_run agent_run
                  WHERE agent_run.id = confirmation.run_id
                    AND agent_run.tenant_id = ?
                    AND agent_run.sales_rep_id = ?
                    AND agent_run.user_id = ?
              )
            """;
    private static final String RISK_HEATMAP_SQL = """
            SELECT
                COALESCE(NULLIF(industry, ''), 'Other') AS industry,
                CASE
                    WHEN UPPER(COALESCE(risk_level, '')) IN ('LOW', 'MEDIUM', 'HIGH')
                    THEN UPPER(risk_level)
                    ELSE 'LOW'
                END AS risk_level,
                COUNT(*) AS count_value
            FROM crm_customer
            WHERE tenant_id = ?
              AND owner_sales_rep_id = ?
            GROUP BY
                COALESCE(NULLIF(industry, ''), 'Other'),
                CASE
                    WHEN UPPER(COALESCE(risk_level, '')) IN ('LOW', 'MEDIUM', 'HIGH')
                    THEN UPPER(risk_level)
                    ELSE 'LOW'
                END
            """;

    private final LeadScoringService leadScoringService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardMetricsService(
            LeadScoringService leadScoringService,
            JdbcTemplate jdbcTemplate
    ) {
        this.leadScoringService = leadScoringService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardMetrics metrics(String tenantId, Long salesRepId, Long userId) {
        List<LeadRecommendation> recommendations = leadScoringService.recommend(tenantId, salesRepId, 20);

        DashboardSummary summary = summary(recommendations, tenantId, salesRepId, userId);
        return new DashboardMetrics(
                salesRepId,
                OffsetDateTime.now(),
                summary,
                leadTrend(recommendations),
                riskHeatmap(tenantId, salesRepId)
        );
    }

    private DashboardSummary summary(
            List<LeadRecommendation> recommendations,
            String tenantId,
            Long salesRepId,
            Long userId
    ) {
        List<LeadRecommendation> highLeads = recommendations.stream()
                .filter(item -> Objects.equals(item.priority(), "HIGH"))
                .toList();
        BigDecimal highLeadAmount = highLeads.stream()
                .map(LeadRecommendation::estimatedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int riskCustomerCount = intCount(HIGH_RISK_CUSTOMER_COUNT_SQL, tenantId, salesRepId);
        int dueTaskCount = intCount(DUE_TASK_COUNT_SQL, tenantId, salesRepId, LocalDateTime.now().plusHours(48));
        int renewalCustomerCount = intCount(RENEWAL_CUSTOMER_COUNT_SQL, tenantId, salesRepId, "%续费%", "%续费%");
        int pendingConfirmationCount = pendingConfirmationCount(tenantId, salesRepId, userId);

        return new DashboardSummary(
                highLeads.size(),
                highLeadAmount,
                riskCustomerCount,
                dueTaskCount,
                renewalCustomerCount,
                pendingConfirmationCount
        );
    }

    private List<DashboardTrendPoint> leadTrend(List<LeadRecommendation> recommendations) {
        Map<String, TrendBucket> buckets = new LinkedHashMap<>();
        recommendations.forEach(lead -> {
            String key = lead.expectedCloseDate() == null
                    ? "unknown"
                    : lead.expectedCloseDate().format(TREND_DATE_FORMATTER);
            TrendBucket bucket = buckets.computeIfAbsent(key, TrendBucket::new);
            bucket.amount = bucket.amount.add(lead.estimatedAmount() == null ? BigDecimal.ZERO : lead.estimatedAmount());
            bucket.total += 1;
            if (Objects.equals(lead.priority(), "HIGH")) {
                bucket.high += 1;
            }
        });

        return buckets.values().stream()
                .sorted(Comparator.comparing((TrendBucket item) -> Objects.equals(item.date, "unknown"))
                        .thenComparing(item -> item.date))
                .limit(7)
                .map(item -> new DashboardTrendPoint(item.date, item.amount, item.high, item.total))
                .toList();
    }

    private DashboardRiskHeatmap riskHeatmap(String tenantId, Long salesRepId) {
        Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
        jdbcTemplate.query(RISK_HEATMAP_SQL, rs -> {
            String industry = blankToOther(rs.getString("industry"));
            String riskLevel = normalizeRiskLevel(rs.getString("risk_level"));
            long count = rs.getLong("count_value");
            counts.computeIfAbsent(industry, ignored -> new LinkedHashMap<>())
                    .put(riskLevel, count);
        }, tenantId, salesRepId);
        List<String> industries = counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<String, Long>> entry) -> industryTotal(entry.getValue()))
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();
        if (industries.isEmpty()) {
            industries = List.of("Other");
        }

        List<DashboardRiskCell> cells = new ArrayList<>();
        int max = 1;
        for (String industry : industries) {
            for (String riskLevel : RISK_LEVELS) {
                int count = counts.getOrDefault(industry, Map.of())
                        .getOrDefault(riskLevel, 0L)
                        .intValue();
                max = Math.max(max, count);
                cells.add(new DashboardRiskCell(industry, riskLevel, count));
            }
        }
        return new DashboardRiskHeatmap(industries, RISK_LEVELS, max, cells);
    }

    private int pendingConfirmationCount(String tenantId, Long salesRepId, Long userId) {
        return intCount(PENDING_CONFIRMATION_COUNT_SQL, tenantId, salesRepId, userId);
    }

    private long industryTotal(Map<String, Long> riskCounts) {
        return riskCounts.values().stream().mapToLong(Long::longValue).sum();
    }

    private int intCount(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count.intValue();
    }

    private String normalizeRiskLevel(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        return RISK_LEVELS.contains(normalized) ? normalized : "LOW";
    }

    private String blankToOther(String value) {
        return value == null || value.isBlank() ? "Other" : value;
    }

    private static final class TrendBucket {
        private final String date;
        private BigDecimal amount = BigDecimal.ZERO;
        private int high;
        private int total;

        private TrendBucket(String date) {
            this.date = date;
        }
    }
}
