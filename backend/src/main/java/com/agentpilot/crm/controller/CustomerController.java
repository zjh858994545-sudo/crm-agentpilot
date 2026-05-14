package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.ContactLog;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CustomerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAuthority('crm:read')")
public class CustomerController {
    private final CustomerService customerService;
    private final ContactLogService contactLogService;

    public CustomerController(CustomerService customerService, ContactLogService contactLogService) {
        this.customerService = customerService;
        this.contactLogService = contactLogService;
    }

    @GetMapping
    public ApiResponse<List<Customer>> list(@RequestParam(required = false) Long salesRepId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .orderByAsc(Customer::getId);
        if (salesRepId != null) {
            wrapper.eq(Customer::getOwnerSalesRepId, salesRepId);
        }
        return ApiResponse.ok(customerService.list(wrapper));
    }

    @GetMapping("/{id}")
    public ApiResponse<Customer> detail(@PathVariable Long id) {
        return ApiResponse.ok(customerService.getById(id));
    }

    @GetMapping("/{id}/contact-logs")
    public ApiResponse<List<ContactLog>> contactLogs(@PathVariable Long id) {
        return ApiResponse.ok(contactLogService.listByCustomerId(id));
    }
}
