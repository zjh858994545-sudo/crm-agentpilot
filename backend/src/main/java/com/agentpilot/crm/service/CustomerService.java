package com.agentpilot.crm.service;

import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.mapper.CustomerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {
    public Optional<Customer> findMentionedIn(String message) {
        return findMentionedIn(message, null, null);
    }

    public Optional<Customer> findMentionedIn(String message, Long salesRepId) {
        return findMentionedIn(message, null, salesRepId);
    }

    public Optional<Customer> findMentionedIn(String message, String tenantId, Long salesRepId) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .apply("{0} LIKE CONCAT('%', name, '%')", message)
                .orderByAsc(Customer::getId)
                .last("limit 1");
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(Customer::getTenantId, tenantId);
        }
        if (salesRepId != null) {
            wrapper.eq(Customer::getOwnerSalesRepId, salesRepId);
        }
        Customer customer = getOne(wrapper, false);
        return Optional.ofNullable(customer);
    }
}
