package com.agentpilot.callcenter.mapper;

import com.agentpilot.callcenter.entity.WebhookReplayGuard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookReplayGuardMapper extends BaseMapper<WebhookReplayGuard> {
}
