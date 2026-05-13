package com.agentpilot.agent.service;

import com.agentpilot.agent.entity.AgentSession;
import com.agentpilot.agent.mapper.AgentSessionMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AgentSessionService extends ServiceImpl<AgentSessionMapper, AgentSession> {
}

