package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.CrmTask;
import com.agentpilot.crm.service.CrmTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final CrmTaskService taskService;

    public TaskController(CrmTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<List<CrmTask>> list(@RequestParam(required = false) Long salesRepId) {
        LambdaQueryWrapper<CrmTask> wrapper = new LambdaQueryWrapper<CrmTask>()
                .orderByAsc(CrmTask::getDueTime);
        if (salesRepId != null) {
            wrapper.eq(CrmTask::getSalesRepId, salesRepId);
        }
        return ApiResponse.ok(taskService.list(wrapper));
    }
}

