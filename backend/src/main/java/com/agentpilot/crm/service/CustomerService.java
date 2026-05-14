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
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        Customer customer = getOne(new LambdaQueryWrapper<Customer>()
                .apply("{0} LIKE CONCAT('%', name, '%')", message)
                .orderByAsc(Customer::getId)
                .last("limit 1"), false);
        return Optional.ofNullable(customer);
    }
}
