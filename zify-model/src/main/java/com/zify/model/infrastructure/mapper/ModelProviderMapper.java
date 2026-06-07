package com.zify.model.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.model.infrastructure.entity.ModelProviderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型供应商 Mapper
 */
@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity> {
}
