package com.dating.match.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus 自动填充时间字段 createdAt / updatedAt。
 */
@Configuration
public class MyBatisPlusConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, OffsetDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now());
    }
}
