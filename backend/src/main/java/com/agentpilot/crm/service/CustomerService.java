package com.agentpilot.crm.service;

import com.agentpilot.crm.entity.Customer;
import com.agentpilot.crm.mapper.CustomerMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {
}

