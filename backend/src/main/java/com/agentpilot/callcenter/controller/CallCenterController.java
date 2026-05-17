package com.agentpilot.callcenter.controller;

import com.agentpilot.callcenter.service.CallCenterService;
import com.agentpilot.callcenter.vo.CallSummaryResponse;
import com.agentpilot.callcenter.vo.CallTextRequest;
import com.agentpilot.callcenter.vo.ContactLogConfirmationResponse;
import com.agentpilot.callcenter.vo.QualityCheckResponse;
import com.agentpilot.callcenter.service.CustomerMemoryService;
import com.agentpilot.callcenter.entity.CustomerMemory;
import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/callcenter")
@PreAuthorize("hasAuthority('crm:read')")
public class CallCenterController {
    private final CallCenterService callCenterService;
    private final CustomerMemoryService customerMemoryService;
    private final CustomerService customerService;
    private final LeadService leadService;

    public CallCenterController(
            CallCenterService callCenterService,
            CustomerMemoryService customerMemoryService,
            CustomerService customerService,
            LeadService leadService
    ) {
        this.callCenterService = callCenterService;
        this.customerMemoryService = customerMemoryService;
        this.customerService = customerService;
        this.leadService = leadService;
    }

    @PostMapping("/summary")
    public ApiResponse<CallSummaryResponse> summarize(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.summarize(securedRequest(request, false)));
    }

    @PostMapping("/quality-check")
    public ApiResponse<QualityCheckResponse> qualityCheck(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.qualityCheck(securedRequest(request, false)));
    }

    @PostMapping("/contact-log-confirmations")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<ContactLogConfirmationResponse> proposeContactLog(@Valid @RequestBody CallTextRequest request) {
        return ApiResponse.ok(callCenterService.proposeContactLog(securedRequest(request, true)));
    }

    @GetMapping("/customers/{customerId}/memory")
    public ApiResponse<List<CustomerMemory>> customerMemory(@PathVariable Long customerId) {
        requireCustomerVisible(customerId);
        return ApiResponse.ok(customerMemoryService.listByCustomerId(customerId));
    }

    private CallTextRequest securedRequest(CallTextRequest request, boolean requireCustomer) {
        Long salesRepId = request.salesRepId();
        Customer customer = null;
        Lead lead = null;
        if (request.customerId() != null) {
            customer = requireCustomerVisible(request.customerId());
            salesRepId = resolveSalesRepId(salesRepId, customer.getOwnerSalesRepId());
        } else if (requireCustomer) {
            throw new AccessDeniedException("customerId is required for CRM write confirmation");
        }
        if (request.leadId() != null) {
            lead = requireLeadVisible(request.leadId());
            salesRepId = resolveSalesRepId(salesRepId, lead.getSalesRepId());
            if (customer != null && !Objects.equals(customer.getId(), lead.getCustomerId())) {
                throw new AccessDeniedException("lead is outside selected customer scope");
            }
        }
        if (salesRepId == null) {
            salesRepId = CurrentUser.salesRepId();
        }
        if (!CurrentUser.canAccessSalesRep(salesRepId)) {
            throw new AccessDeniedException("request.salesRepId is outside current data scope");
        }
        return new CallTextRequest(request.customerId(), salesRepId, request.leadId(), request.text());
    }

    private Customer requireCustomerVisible(Long customerId) {
        Customer customer = customerService.getById(customerId);
        if (customer == null
                || !CurrentUser.tenantId().equals(customer.getTenantId())
                || !CurrentUser.canAccessSalesRep(customer.getOwnerSalesRepId())) {
            throw new AccessDeniedException("customer is outside current data scope");
        }
        return customer;
    }

    private Lead requireLeadVisible(Long leadId) {
        Lead lead = leadService.getById(leadId);
        if (lead == null
                || !CurrentUser.tenantId().equals(lead.getTenantId())
                || !CurrentUser.canAccessSalesRep(lead.getSalesRepId())) {
            throw new AccessDeniedException("lead is outside current data scope");
        }
        return lead;
    }

    private Long resolveSalesRepId(Long requestedSalesRepId, Long ownerSalesRepId) {
        if (requestedSalesRepId == null) {
            return ownerSalesRepId;
        }
        if (!Objects.equals(requestedSalesRepId, ownerSalesRepId)) {
            throw new AccessDeniedException("request.salesRepId does not match customer or lead owner");
        }
        return requestedSalesRepId;
    }
}
