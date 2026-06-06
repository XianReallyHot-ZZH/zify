package com.zify.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis 配置
 * <p>
 * Key 使用 String 序列化，Value 使用 JSON 序列化（携带类型信息）。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(buildObjectMapper());

        // Key 使用 String
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建 Redis 专用 ObjectMapper
     * <ul>
     *     <li>为非 final 类添加 @class 类型信息（作为 JSON 属性存储）</li>
     *     <li>Java Time 模块已内置在 jackson-databind 3.x，默认支持</li>
     * </ul>
     */
    private ObjectMapper buildObjectMapper() {
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfBaseType("com.zify.")      // 允许 zify 项目下的类
                .allowIfBaseType("java.util.")     // 允许常用集合
                .allowIfSubType(Number.class)      // 允许数字类型
                .allowIfBaseType("java.time.")     // 允许时间类型 ⭐
                .build();

        return JsonMapper.builder()
                .activateDefaultTyping(
                        ptv,
                        DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY)
                .build();
    }
}
