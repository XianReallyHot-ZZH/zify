package com.zify.tool.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.tool.infrastructure.entity.ToolCallLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具调用日志 Mapper（大表）。
 */
@Mapper
public interface ToolCallLogMapper extends BaseMapper<ToolCallLogEntity> {
}
