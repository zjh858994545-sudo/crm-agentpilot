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
        return listByCustomerId(customerId, null);
    }

    public List<ContactLog> listByCustomerId(Long customerId, String tenantId) {
        LambdaQueryWrapper<ContactLog> wrapper = new LambdaQueryWrapper<ContactLog>()
                .eq(ContactLog::getCustomerId, customerId);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(ContactLog::getTenantId, tenantId);
        }
        return list(wrapper.orderByDesc(ContactLog::getContactAt));
    }

    public List<ContactLog> listByCustomerIds(List<Long> customerIds, String tenantId) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ContactLog> wrapper = new LambdaQueryWrapper<ContactLog>()
                .in(ContactLog::getCustomerId, customerIds);
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(ContactLog::getTenantId, tenantId);
        }
        return list(wrapper.orderByDesc(ContactLog::getContactAt));
    }
}
