package com.zify.model.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.model.infrastructure.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper
 */
@Mapper
public interface ModelMapper extends BaseMapper<ModelEntity> {
}
