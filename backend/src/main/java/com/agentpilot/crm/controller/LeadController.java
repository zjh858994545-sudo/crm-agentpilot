package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.scoring.service.LeadScoringService;
import com.agentpilot.scoring.vo.LeadRecommendation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadService leadService;
    private final LeadScoringService leadScoringService;

    public LeadController(LeadService leadService, LeadScoringService leadScoringService) {
        this.leadService = leadService;
        this.leadScoringService = leadScoringService;
    }

    @GetMapping
    public ApiResponse<List<Lead>> list(@RequestParam(required = false) Long salesRepId) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<Lead>()
                .orderByAsc(Lead::getId);
        if (salesRepId != null) {
            wrapper.eq(Lead::getSalesRepId, salesRepId);
        }
        return ApiResponse.ok(leadService.list(wrapper));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<LeadRecommendation>> recommend(
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(defaultValue = "10") int topK
    ) {
        return ApiResponse.ok(leadScoringService.recommend(salesRepId, topK));
    }
}
