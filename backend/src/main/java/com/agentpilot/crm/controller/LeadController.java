package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.agentpilot.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@PreAuthorize("hasAuthority('crm:read')")
public class LeadController {
    private final LeadService leadService;
    private final LeadScoringService leadScoringService;

    public LeadController(LeadService leadService, LeadScoringService leadScoringService) {
        this.leadService = leadService;
        this.leadScoringService = leadScoringService;
    }

    @GetMapping
    public ApiResponse<List<Lead>> list(@RequestParam(required = false) Long salesRepId) {
        Long scopedSalesRepId = scopedSalesRepId(salesRepId);
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<Lead>()
                .eq(Lead::getSalesRepId, scopedSalesRepId)
                .orderByAsc(Lead::getId);
        return ApiResponse.ok(leadService.list(wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<Lead> detail(@PathVariable Long id) {
        Lead lead = leadService.getById(id);
        requireLeadVisible(lead);
        return ApiResponse.ok(lead);
    }

    @GetMapping("/recommend")
    public ApiResponse<List<LeadRecommendation>> recommend(
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(defaultValue = "10") int topK
    ) {
        return ApiResponse.ok(leadScoringService.recommend(scopedSalesRepId(salesRepId), topK));
    }

    private Long scopedSalesRepId(Long requestedSalesRepId) {
        Long currentSalesRepId = CurrentUser.salesRepId();
        if (requestedSalesRepId != null && !requestedSalesRepId.equals(currentSalesRepId)) {
            throw new AccessDeniedException("salesRepId is outside current data scope");
        }
        return currentSalesRepId;
    }

    private void requireLeadVisible(Lead lead) {
        if (lead == null || !CurrentUser.salesRepId().equals(lead.getSalesRepId())) {
            throw new AccessDeniedException("lead is outside current data scope");
        }
    }
}
