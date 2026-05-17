package com.agentpilot.crm.controller;

import com.agentpilot.common.response.ApiResponse;
import com.agentpilot.common.response.PageResponse;
import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.service.ContactLogService;
import com.agentpilot.crm.service.CustomerService;
import com.agentpilot.crm.vo.ContactLogView;
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
                .eq(Customer::getTenantId, CurrentUser.tenantId())
                .eq(Customer::getOwnerSalesRepId, scopedSalesRepId)
                .orderByAsc(Customer::getId);
        return ApiResponse.ok(customerService.list(wrapper).stream().map(CustomerView::from).toList());
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<CustomerView>> page(
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String keyword
    ) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = (long) (safePage - 1) * safePageSize;
        Long scopedSalesRepId = scopedSalesRepId(salesRepId);
        LambdaQueryWrapper<Customer> countWrapper = customerQuery(scopedSalesRepId, keyword);
        long total = customerService.count(countWrapper);
        List<CustomerView> items = customerService.list(customerQuery(scopedSalesRepId, keyword)
                        .orderByAsc(Customer::getId)
                        .last("limit " + safePageSize + " offset " + offset))
                .stream()
                .map(CustomerView::from)
                .toList();
        return ApiResponse.ok(new PageResponse<>(items, total, safePage, safePageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerView> detail(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        requireCustomerVisible(customer);
        return ApiResponse.ok(CustomerView.from(customer));
    }

    @GetMapping("/{id}/contact-logs")
    public ApiResponse<List<ContactLogView>> contactLogs(@PathVariable Long id) {
        requireCustomerVisible(customerService.getById(id));
        return ApiResponse.ok(contactLogService.listByCustomerId(id, CurrentUser.tenantId())
                .stream()
                .map(ContactLogView::from)
                .toList());
    }

    private Long scopedSalesRepId(Long requestedSalesRepId) {
        Long currentSalesRepId = CurrentUser.salesRepId();
        if (requestedSalesRepId != null && !requestedSalesRepId.equals(currentSalesRepId)) {
            throw new AccessDeniedException("salesRepId is outside current data scope");
        }
        return currentSalesRepId;
    }

    private LambdaQueryWrapper<Customer> customerQuery(Long salesRepId, String keyword) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantId, CurrentUser.tenantId())
                .eq(Customer::getOwnerSalesRepId, salesRepId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(item -> item
                    .like(Customer::getName, value)
                    .or()
                    .like(Customer::getIndustry, value)
                    .or()
                    .like(Customer::getCity, value)
                    .or()
                    .like(Customer::getTags, value)
                    .or()
                    .like(Customer::getContactName, value));
        }
        return wrapper;
    }

    private void requireCustomerVisible(Customer customer) {
        if (customer == null
                || !CurrentUser.tenantId().equals(customer.getTenantId())
                || !CurrentUser.salesRepId().equals(customer.getOwnerSalesRepId())) {
            throw new AccessDeniedException("customer is outside current data scope");
        }
    }
}
