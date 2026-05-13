package com.agentpilot.crm.service;

import com.agentpilot.crm.entity.Lead;
import com.agentpilot.crm.mapper.LeadMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class LeadService extends ServiceImpl<LeadMapper, Lead> {
}

