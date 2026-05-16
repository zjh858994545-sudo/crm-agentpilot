package com.agentpilot.agent.service;

import com.agentpilot.agent.entity.AgentConfirmation;
import com.agentpilot.agent.mapper.AgentConfirmationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentConfirmationService extends ServiceImpl<AgentConfirmationMapper, AgentConfirmation> {
    public List<AgentConfirmation> listByRunId(Long runId) {
        return list(new LambdaQueryWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getRunId, runId)
                .orderByAsc(AgentConfirmation::getId));
    }

    public boolean claimPendingForConfirm(Long confirmationId, Long userId) {
        return update(new LambdaUpdateWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getId, confirmationId)
                .eq(AgentConfirmation::getStatus, "PENDING")
                .set(AgentConfirmation::getStatus, "PROCESSING")
                .set(AgentConfirmation::getConfirmedBy, userId)
                .set(AgentConfirmation::getConfirmedAt, LocalDateTime.now()));
    }

    public boolean rejectPending(Long confirmationId, Long userId) {
        return update(new LambdaUpdateWrapper<AgentConfirmation>()
                .eq(AgentConfirmation::getId, confirmationId)
                .eq(AgentConfirmation::getStatus, "PENDING")
                .set(AgentConfirmation::getStatus, "REJECTED")
                .set(AgentConfirmation::getConfirmedBy, userId)
                .set(AgentConfirmation::getConfirmedAt, LocalDateTime.now()));
    }
}
