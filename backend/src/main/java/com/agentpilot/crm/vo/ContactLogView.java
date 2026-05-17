package com.agentpilot.crm.vo;

import com.agentpilot.common.security.DataMasking;
import com.agentpilot.crm.entity.ContactLog;

import java.time.LocalDateTime;

public record ContactLogView(
        Long id,
        Long customerId,
        Long salesRepId,
        Long leadId,
        String channel,
        String content,
        String summary,
        String customerIntent,
        String objections,
        String nextAction,
        LocalDateTime contactAt,
        LocalDateTime createdAt
) {
    public static ContactLogView from(ContactLog log) {
        return new ContactLogView(
                log.getId(),
                log.getCustomerId(),
                log.getSalesRepId(),
                log.getLeadId(),
                log.getChannel(),
                DataMasking.maskSensitiveText(log.getContent()),
                DataMasking.maskSensitiveText(log.getSummary()),
                log.getCustomerIntent(),
                DataMasking.maskSensitiveText(log.getObjections()),
                DataMasking.maskSensitiveText(log.getNextAction()),
                log.getContactAt(),
                log.getCreatedAt()
        );
    }
}
