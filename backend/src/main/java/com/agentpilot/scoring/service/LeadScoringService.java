package com.agentpilot.scoring.service;

import com.agentpilot.crm.entity.ContactLog;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CrmTaskService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class LeadScoringService {
    private static final Set<String> PRIORITY_INDUSTRIES = Set.of("房产", "招聘", "本地生活服务");

    private final LeadService leadService;
    private final CustomerService customerService;
    private final ContactLogService contactLogService;
    private final CrmTaskService taskService;

    public LeadScoringService(
            LeadService leadService,
            CustomerService customerService,
            ContactLogService contactLogService,
            CrmTaskService taskService
    ) {
        this.leadService = leadService;
        this.customerService = customerService;
        this.contactLogService = contactLogService;
        this.taskService = taskService;
    }

    public List<LeadRecommendation> recommend(Long salesRepId, int topK) {
        int limit = Math.max(1, Math.min(topK, 20));
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<Lead>()
                .eq(Lead::getStatus, "OPEN");
        if (salesRepId != null) {
            wrapper.eq(Lead::getSalesRepId, salesRepId);
        }

        return leadService.list(wrapper)
                .stream()
                .map(this::scoreLead)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(LeadRecommendation::score).reversed())
                .limit(limit)
                .toList();
    }

    private Optional<LeadRecommendation> scoreLead(Lead lead) {
        Customer customer = customerService.getById(lead.getCustomerId());
        if (customer == null) {
            return Optional.empty();
        }

        List<ContactLog> logs = contactLogService.listByCustomerId(customer.getId());
        List<CrmTask> tasks = taskService.list(new LambdaQueryWrapper<CrmTask>()
                .eq(CrmTask::getCustomerId, customer.getId()));
        List<String> reasons = new ArrayList<>();
        double score = 20.0;
        LocalDateTime now = LocalDateTime.now();

        score += scoreRecentContact(customer, now, reasons);
        score += scorePackageExpire(customer, now, reasons);
        score += scoreCustomerValue(customer, reasons);
        score += scoreIntent(lead, reasons);
        score += scoreHistoricalObjections(logs, reasons);
        score += scoreTaskStatus(tasks, now, reasons);
        score += scoreIndustry(customer, reasons);
        score += scoreStage(lead, reasons);

        double normalizedScore = Math.min(100.0, round(score));
        String priority = priorityOf(normalizedScore);

        return Optional.of(new LeadRecommendation(
                lead.getId(),
                customer.getId(),
                customer.getName(),
                customer.getIndustry(),
                lead.getEstimatedAmount(),
                lead.getExpectedCloseDate(),
                normalizedScore,
                priority,
                reasons,
                suggestedAction(customer, lead, reasons)
        ));
    }

    private double scoreRecentContact(Customer customer, LocalDateTime now, List<String> reasons) {
        if (customer.getLastContactAt() == null) {
            reasons.add("客户暂无最近联系记录");
            return 12.0;
        }
        long days = Duration.between(customer.getLastContactAt(), now).toDays();
        if (days >= 14) {
            reasons.add("最近 " + days + " 天未联系，存在沉默风险");
            return 16.0;
        }
        if (days >= 7) {
            reasons.add("最近 " + days + " 天未联系，需要及时跟进");
            return 10.0;
        }
        return 2.0;
    }

    private double scorePackageExpire(Customer customer, LocalDateTime now, List<String> reasons) {
        if (customer.getPackageExpireAt() == null) {
            return 0.0;
        }
        long days = Duration.between(now, customer.getPackageExpireAt()).toDays();
        if (days >= 0 && days <= 15) {
            reasons.add("套餐将在 " + days + " 天后到期");
            return 22.0;
        }
        if (days > 15 && days <= 30) {
            reasons.add("套餐将在 " + days + " 天后到期，可提前启动续费沟通");
            return 14.0;
        }
        return 3.0;
    }

    private double scoreCustomerValue(Customer customer, List<String> reasons) {
        Map<String, Double> scores = Map.of("A", 16.0, "B", 9.0, "C", 3.0);
        double value = scores.getOrDefault(customer.getValueLevel(), 0.0);
        if (value >= 9.0) {
            reasons.add(customer.getValueLevel() + " 类客户，具备较高经营价值");
        }
        return value;
    }

    private double scoreIntent(Lead lead, List<String> reasons) {
        if (Objects.equals(lead.getIntentLevel(), "HIGH")) {
            reasons.add("商机意向等级为 HIGH");
            return 16.0;
        }
        if (Objects.equals(lead.getIntentLevel(), "MEDIUM")) {
            reasons.add("商机意向等级为 MEDIUM");
            return 8.0;
        }
        return 2.0;
    }

    private double scoreHistoricalObjections(List<ContactLog> logs, List<String> reasons) {
        boolean hasPriceObjection = logs.stream()
                .map(ContactLog::getObjections)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("价格") || text.contains("预算"));
        boolean hasQualityConcern = logs.stream()
                .map(ContactLog::getObjections)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("效果") || text.contains("线索") || text.contains("咨询"));

        double score = 0.0;
        if (hasPriceObjection) {
            reasons.add("历史沟通中存在价格或预算异议");
            score += 7.0;
        }
        if (hasQualityConcern) {
            reasons.add("历史沟通中关注效果、线索或咨询质量");
            score += 6.0;
        }
        return score;
    }

    private double scoreTaskStatus(List<CrmTask> tasks, LocalDateTime now, List<String> reasons) {
        boolean hasOverdueTask = tasks.stream()
                .anyMatch(task -> Objects.equals(task.getStatus(), "PENDING") && task.getDueTime().isBefore(now));
        if (hasOverdueTask) {
            reasons.add("存在已到期未完成跟进任务");
            return 9.0;
        }
        boolean hasPendingTask = tasks.stream()
                .anyMatch(task -> Objects.equals(task.getStatus(), "PENDING"));
        if (hasPendingTask) {
            reasons.add("已有待办任务，需要按计划推进");
            return 4.0;
        }
        return 0.0;
    }

    private double scoreIndustry(Customer customer, List<String> reasons) {
        if (PRIORITY_INDUSTRIES.contains(customer.getIndustry())) {
            reasons.add(customer.getIndustry() + " 属于本项目重点行业");
            return 7.0;
        }
        if (Set.of("餐饮", "家政", "教育培训").contains(customer.getIndustry())) {
            reasons.add(customer.getIndustry() + " 具备本地生活销售场景代表性");
            return 5.0;
        }
        return 2.0;
    }

    private double scoreStage(Lead lead, List<String> reasons) {
        if (Objects.equals(lead.getStage(), "NEGOTIATING")) {
            reasons.add("商机处于谈判阶段，适合优先推进");
            return 8.0;
        }
        if (Objects.equals(lead.getStage(), "OBJECTION")) {
            reasons.add("商机存在异议，需要及时处理");
            return 6.0;
        }
        if (Objects.equals(lead.getStage(), "QUALIFIED")) {
            return 4.0;
        }
        return 2.0;
    }

    private String priorityOf(double score) {
        if (score >= 75.0) {
            return "HIGH";
        }
        if (score >= 60.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String suggestedAction(Customer customer, Lead lead, List<String> reasons) {
        String reasonText = String.join("；", reasons);
        if (reasonText.contains("价格") || reasonText.contains("预算")) {
            return "优先电话跟进，围绕 ROI、曝光数据和同行案例处理价格异议";
        }
        if (reasonText.contains("到期")) {
            return "准备最近 30 天曝光、电话线索和转化数据，启动续费复盘";
        }
        if (Objects.equals(lead.getIntentLevel(), "HIGH")) {
            return "约定明确下一步时间，输出套餐对比和可执行投放方案";
        }
        return "保持微信触达并补充行业案例，确认客户预算、决策人和下一步时间";
    }

    private double round(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

