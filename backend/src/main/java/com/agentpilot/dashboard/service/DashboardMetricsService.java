package com.agentpilot.dashboard.service;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.entity.AgentRun;
import com.agentpilot.agent.service.AgentConfirmationService;
import com.agentpilot.agent.service.AgentRunService;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.CrmTaskService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.dashboard.vo.DashboardMetrics;
import com.agentpilot.dashboard.vo.DashboardRiskCell;
import com.agentpilot.dashboard.vo.DashboardRiskHeatmap;
import com.agentpilot.dashboard.vo.DashboardSummary;
import com.agentpilot.dashboard.vo.DashboardTrendPoint;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardMetricsService {
    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<String> OPEN_TASK_STATUSES = Set.of("TODO", "PENDING", "OPEN");
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    private final LeadScoringService leadScoringService;
    private final CustomerService customerService;
    private final CrmTaskService taskService;
    private final AgentRunService runService;
    private final AgentConfirmationService confirmationService;

    public DashboardMetricsService(
            LeadScoringService leadScoringService,
            CustomerService customerService,
            CrmTaskService taskService,
            AgentRunService runService,
            AgentConfirmationService confirmationService
    ) {
        this.leadScoringService = leadScoringService;
        this.customerService = customerService;
        this.taskService = taskService;
        this.runService = runService;
        this.confirmationService = confirmationService;
    }

    public DashboardMetrics metrics(Long salesRepId, Long userId) {
        List<LeadRecommendation> recommendations = leadScoringService.recommend(salesRepId, 20);
        List<Customer> customers = customerService.list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getOwnerSalesRepId, salesRepId)
                .orderByAsc(Customer::getId));
        List<CrmTask> tasks = taskService.list(new LambdaQueryWrapper<CrmTask>()
                .eq(CrmTask::getSalesRepId, salesRepId)
                .orderByAsc(CrmTask::getDueTime));

        DashboardSummary summary = summary(recommendations, customers, tasks, salesRepId, userId);
        return new DashboardMetrics(
                salesRepId,
                OffsetDateTime.now(),
                summary,
                leadTrend(recommendations),
                riskHeatmap(customers)
        );
    }

    private DashboardSummary summary(
            List<LeadRecommendation> recommendations,
            List<Customer> customers,
            List<CrmTask> tasks,
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
        int riskCustomerCount = (int) customers.stream()
                .filter(customer -> Objects.equals(normalizeRiskLevel(customer.getRiskLevel()), "HIGH"))
                .count();
        int dueTaskCount = (int) tasks.stream()
                .filter(this::isOpenDueSoon)
                .count();
        int renewalCustomerCount = (int) customers.stream()
                .filter(this::isRenewalCustomer)
                .count();
        int pendingConfirmationCount = pendingConfirmationCount(salesRepId, userId);

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

    private DashboardRiskHeatmap riskHeatmap(List<Customer> customers) {
        List<String> industries = customers.stream()
                .map(customer -> blankToOther(customer.getIndustry()))
                .distinct()
                .limit(5)
                .toList();
        if (industries.isEmpty()) {
            industries = List.of("Other");
        }

        Map<String, Map<String, Long>> counts = customers.stream()
                .collect(Collectors.groupingBy(
                        customer -> blankToOther(customer.getIndustry()),
                        Collectors.groupingBy(customer -> normalizeRiskLevel(customer.getRiskLevel()), Collectors.counting())
                ));

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

    private int pendingConfirmationCount(Long salesRepId, Long userId) {
        List<Long> runIds = runService.list(new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getSalesRepId, salesRepId)
                        .eq(AgentRun::getUserId, userId)
                        .select(AgentRun::getId))
                .stream()
                .map(AgentRun::getId)
                .toList();
        if (runIds.isEmpty()) {
            return 0;
        }
        return (int) confirmationService.count(new LambdaQueryWrapper<AgentConfirmation>()
                .in(AgentConfirmation::getRunId, runIds)
                .eq(AgentConfirmation::getStatus, "PENDING"));
    }

    private boolean isOpenDueSoon(CrmTask task) {
        if (task.getDueTime() == null) {
            return false;
        }
        String status = task.getStatus() == null ? "" : task.getStatus().toUpperCase(Locale.ROOT);
        return OPEN_TASK_STATUSES.contains(status)
                && !task.getDueTime().isAfter(LocalDateTime.now().plusHours(48));
    }

    private boolean isRenewalCustomer(Customer customer) {
        return containsRenewal(customer.getLifecycleStage()) || containsRenewal(customer.getTags());
    }

    private boolean containsRenewal(String value) {
        return value != null && value.contains("\u7eed\u8d39");
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
