package com.zify.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.agent.infrastructure.entity.AgentToolEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 与工具关联 Mapper（归属 agent 模块）。
 */
@Mapper
public interface AgentToolMapper extends BaseMapper<AgentToolEntity> {
}
