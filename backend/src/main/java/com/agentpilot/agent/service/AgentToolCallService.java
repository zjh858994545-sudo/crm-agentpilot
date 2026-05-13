package com.agentpilot.agent.service;

import com.agentpilot.agent.entity.AgentToolCall;
import com.agentpilot.agent.mapper.AgentToolCallMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentToolCallService extends ServiceImpl<AgentToolCallMapper, AgentToolCall> {

    public List<AgentToolCall> listByRunId(Long runId) {
        return list(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getRunId, runId)
                .orderByAsc(AgentToolCall::getId));
    }
}

