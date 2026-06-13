package com.zify.common.persistence.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.zify.common.persistence.id.IdGenerator;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus meta object handler for common fields.
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // id 使用 setFieldValByName 而非 strictInsertFill，因为 @TableId 字段可能被 strict 模式跳过
        if (metaObject.getValue("id") == null) {
            this.setFieldValByName("id", IdGenerator.uuid(), metaObject);
        }
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
