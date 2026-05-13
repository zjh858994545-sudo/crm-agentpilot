package com.agentpilot.callcenter.controller;

import com.agentpilot.callcenter.service.CallCenterService;
import com.agentpilot.callcenter.vo.CallSummaryResponse;
import com.agentpilot.callcenter.vo.CallTextRequest;
import com.agentpilot.callcenter.vo.ContactLogConfirmationResponse;
import com.agentpilot.callcenter.vo.QualityCheckResponse;
import com.agentpilot.callcenter.service.CustomerMemoryService;
import com.agentpilot.callcenter.entity.CustomerMemory;
import com.agentpilot.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/callcenter")
public class CallCenterController {
    private final CallCenterService callCenterService;
    private final CustomerMemoryService customerMemoryService;

    public CallCenterController(CallCenterService callCenterService, CustomerMemoryService customerMemoryService) {
        this.callCenterService = callCenterService;
        this.customerMemoryService = customerMemoryService;
    }

    @PostMapping("/summary")
    public ApiResponse<CallSummaryResponse> summarize(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.summarize(request));
    }

    @PostMapping("/quality-check")
    public ApiResponse<QualityCheckResponse> qualityCheck(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.qualityCheck(request));
    }

    @PostMapping("/contact-log-confirmations")
    public ApiResponse<ContactLogConfirmationResponse> proposeContactLog(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.proposeContactLog(request));
    }

    @GetMapping("/customers/{customerId}/memory")
    public ApiResponse<List<CustomerMemory>> customerMemory(@PathVariable Long customerId) {
        return ApiResponse.ok(customerMemoryService.listByCustomerId(customerId));
    }
}

