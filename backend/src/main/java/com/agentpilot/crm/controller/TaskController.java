package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.crm.service.CrmTaskService;
import com.agentpilot.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAuthority('crm:read')")
public class TaskController {
    private final CrmTaskService taskService;

    public TaskController(CrmTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<List<CrmTask>> list(@RequestParam(required = false) Long salesRepId) {
        Long scopedSalesRepId = scopedSalesRepId(salesRepId);
        LambdaQueryWrapper<CrmTask> wrapper = new LambdaQueryWrapper<CrmTask>()
                .eq(CrmTask::getTenantId, CurrentUser.tenantId())
                .eq(CrmTask::getSalesRepId, scopedSalesRepId)
                .orderByAsc(CrmTask::getDueTime);
        return ApiResponse.ok(taskService.list(wrapper));
    }

    private Long scopedSalesRepId(Long requestedSalesRepId) {
        return CurrentUser.scopedSalesRepId(requestedSalesRepId);
    }
}
