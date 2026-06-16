package com.zify.tool.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.tool.infrastructure.entity.ToolEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具定义 Mapper。
 */
@Mapper
public interface ToolMapper extends BaseMapper<ToolEntity> {
}
