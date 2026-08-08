package com.dating.user.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/** MyBatis-Plus 自动填充：自动处理 createdAt / updatedAt ，所有实体类不需要手动设置时间。*/

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
