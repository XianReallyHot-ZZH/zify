package com.zify.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zify.chat.infrastructure.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper。
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
}
