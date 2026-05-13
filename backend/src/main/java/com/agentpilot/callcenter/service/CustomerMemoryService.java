package com.agentpilot.callcenter.service;

import com.agentpilot.callcenter.entity.CustomerMemory;
import com.agentpilot.callcenter.mapper.CustomerMemoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerMemoryService extends ServiceImpl<CustomerMemoryMapper, CustomerMemory> {

    public List<CustomerMemory> listByCustomerId(Long customerId) {
        return list(new LambdaQueryWrapper<CustomerMemory>()
                .eq(CustomerMemory::getCustomerId, customerId)
                .orderByDesc(CustomerMemory::getId));
    }
}

