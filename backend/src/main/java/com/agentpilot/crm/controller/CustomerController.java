package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.crm.entity.ContactLog;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.vo.CustomerView;
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
    public ApiResponse<List<CustomerView>> list(@RequestParam(required = false) Long salesRepId) {
        Long scopedSalesRepId = scopedSalesRepId(salesRepId);
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getOwnerSalesRepId, scopedSalesRepId)
                .orderByAsc(Customer::getId);
        return ApiResponse.ok(customerService.list(wrapper).stream().map(CustomerView::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerView> detail(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        requireCustomerVisible(customer);
        return ApiResponse.ok(CustomerView.from(customer));
    }

    @GetMapping("/{id}/contact-logs")
    public ApiResponse<List<ContactLog>> contactLogs(@PathVariable Long id) {
        requireCustomerVisible(customerService.getById(id));
        return ApiResponse.ok(contactLogService.listByCustomerId(id));
    }

    private Long scopedSalesRepId(Long requestedSalesRepId) {
        Long currentSalesRepId = CurrentUser.salesRepId();
        if (requestedSalesRepId != null && !requestedSalesRepId.equals(currentSalesRepId)) {
            throw new AccessDeniedException("salesRepId is outside current data scope");
        }
        return currentSalesRepId;
    }

    private void requireCustomerVisible(Customer customer) {
        if (customer == null || !CurrentUser.salesRepId().equals(customer.getOwnerSalesRepId())) {
            throw new AccessDeniedException("customer is outside current data scope");
        }
    }
}
