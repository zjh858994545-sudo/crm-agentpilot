package com.agentpilot.crm.vo;

import com.agentpilot.common.security.DataMasking;
import com.agentpilot.crm.entity.Customer;

import java.time.LocalDateTime;

public record CustomerView(
        Long id,
        String name,
        String industry,
        String city,
        String address,
        String contactName,
        String contactMobile,
        String lifecycleStage,
        String valueLevel,
        String riskLevel,
        Long ownerSalesRepId,
        LocalDateTime lastContactAt,
        LocalDateTime nextFollowTime,
        LocalDateTime packageExpireAt,
        String tags,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerView from(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                customer.getIndustry(),
                customer.getCity(),
                customer.getAddress(),
                customer.getContactName(),
                DataMasking.maskMobile(customer.getContactMobile()),
                customer.getLifecycleStage(),
                customer.getValueLevel(),
                customer.getRiskLevel(),
                customer.getOwnerSalesRepId(),
                customer.getLastContactAt(),
                customer.getNextFollowTime(),
                customer.getPackageExpireAt(),
                customer.getTags(),
                customer.getRemark(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

}
