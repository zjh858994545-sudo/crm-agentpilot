package com.agentpilot.callcenter.controller;

import com.agentpilot.callcenter.config.CallProviderProperties;
import com.agentpilot.callcenter.service.CallCenterService;
import com.agentpilot.callcenter.vo.CallEndedEventRequest;
import com.agentpilot.callcenter.vo.CallEndedEventResponse;
import com.agentpilot.callcenter.vo.CallSummaryResponse;
import com.agentpilot.callcenter.vo.CallTextRequest;
import com.agentpilot.callcenter.vo.ContactLogConfirmationResponse;
import com.agentpilot.callcenter.vo.QualityCheckResponse;
import com.agentpilot.callcenter.service.CustomerMemoryService;
import com.agentpilot.callcenter.service.WebhookSecurityService;
import com.agentpilot.callcenter.entity.CustomerMemory;
import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.service.LeadService;
import com.agentpilot.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/callcenter")
@PreAuthorize("hasAuthority('crm:read')")
public class CallCenterController {
    private final CallCenterService callCenterService;
    private final CustomerMemoryService customerMemoryService;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final WebhookSecurityService webhookSecurityService;
    private final CallProviderProperties callProviderProperties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public CallCenterController(
            CallCenterService callCenterService,
            CustomerMemoryService customerMemoryService,
            CustomerService customerService,
            LeadService leadService,
            WebhookSecurityService webhookSecurityService,
            CallProviderProperties callProviderProperties,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.callCenterService = callCenterService;
        this.customerMemoryService = customerMemoryService;
        this.customerService = customerService;
        this.leadService = leadService;
        this.webhookSecurityService = webhookSecurityService;
        this.callProviderProperties = callProviderProperties;
        this.objectMapper = objectMapper;
        this.validator = validator;
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

    @PostMapping("/call-ended-events")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<CallEndedEventResponse> callEnded(
            @RequestHeader(value = "X-AgentPilot-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-AgentPilot-Webhook-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-AgentPilot-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        webhookSecurityService.verifyCallEndedEvent(CurrentUser.tenantId(), rawBody, timestamp, nonce, signature);
        CallEndedEventRequest request = parseAndValidateCallEndedEvent(rawBody);
        return ApiResponse.ok(processCallEndedEvent(request));
    }

    @PostMapping("/call-ended-events/internal")
    @PreAuthorize("hasAuthority('crm:write')")
    public ApiResponse<CallEndedEventResponse> internalCallEnded(@Valid @RequestBody CallEndedEventRequest request) {
        return ApiResponse.ok(processCallEndedEvent(request));
    }

    private CallEndedEventResponse processCallEndedEvent(CallEndedEventRequest request) {
        CallTextRequest secured = securedRequest(request.toCallTextRequest(), true);
        return callCenterService.handleCallEnded(request.callId(), request.recordingUrl(), secured);
    }

    @GetMapping("/customers/{customerId}/memory")
    public ApiResponse<List<CustomerMemory>> customerMemory(@PathVariable Long customerId) {
        requireCustomerVisible(customerId);
        return ApiResponse.ok(customerMemoryService.listByCustomerId(customerId));
    }

    @GetMapping("/webhook/status")
    @PreAuthorize("hasAuthority('ops:read')")
    public ApiResponse<Map<String, Object>> webhookStatus() {
        return ApiResponse.ok(Map.of(
                "signatureEnabled", webhookSecurityService.enabled(),
                "secretConfigured", webhookSecurityService.secretConfigured(),
                "maxSkewSeconds", webhookSecurityService.maxSkewSeconds(),
                "replayProtection", true
        ));
    }

    @GetMapping("/provider/status")
    @PreAuthorize("hasAuthority('ops:read')")
    public ApiResponse<Map<String, Object>> providerStatus() {
        return ApiResponse.ok(Map.of(
                "provider", callProviderProperties.getProvider(),
                "enabled", callProviderProperties.isEnabled(),
                "endpointConfigured", callProviderProperties.endpointConfigured(),
                "asrProvider", callProviderProperties.getAsrProvider(),
                "asrModel", callProviderProperties.getAsrModel(),
                "asrEnabled", callProviderProperties.isAsrEnabled()
        ));
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

    private CallEndedEventRequest parseAndValidateCallEndedEvent(String rawBody) {
        try {
            CallEndedEventRequest request = objectMapper.readValue(rawBody, CallEndedEventRequest.class);
            Set<ConstraintViolation<CallEndedEventRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid call-ended event JSON");
        }
    }
}
