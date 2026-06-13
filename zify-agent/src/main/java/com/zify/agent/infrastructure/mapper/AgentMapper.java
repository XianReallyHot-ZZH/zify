package com.zify.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.agent.infrastructure.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper。
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
