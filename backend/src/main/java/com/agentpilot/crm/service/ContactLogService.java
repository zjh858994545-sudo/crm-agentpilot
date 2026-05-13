package com.agentpilot.crm.service;

import com.agentpilot.crm.entity.ContactLog;
import com.agentpilot.crm.mapper.ContactLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactLogService extends ServiceImpl<ContactLogMapper, ContactLog> {

    public List<ContactLog> listByCustomerId(Long customerId) {
        return list(new LambdaQueryWrapper<ContactLog>()
                .eq(ContactLog::getCustomerId, customerId)
                .orderByDesc(ContactLog::getContactAt));
    }
}

