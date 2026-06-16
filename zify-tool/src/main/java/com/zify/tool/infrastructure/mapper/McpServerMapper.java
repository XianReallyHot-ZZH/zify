package com.zify.tool.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP Server Mapper。
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {
}
