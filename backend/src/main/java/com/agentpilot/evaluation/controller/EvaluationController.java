package com.agentpilot.evaluation.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.evaluation.service.EvaluationService;
import com.agentpilot.evaluation.vo.EvaluationReport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluation")
@PreAuthorize("hasAuthority('evaluation:run')")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public ApiResponse<EvaluationReport> run() {
        return ApiResponse.ok(evaluationService.runEvaluation());
    }
}
