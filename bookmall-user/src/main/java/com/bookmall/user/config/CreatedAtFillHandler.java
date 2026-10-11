package com.bookmall.user.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 插入时补上创建时间。字段带 {@code FieldFill.INSERT} 且仍为空才会写入，已有值不会被覆盖。
 */
@Component
public class CreatedAtFillHandler implements MetaObjectHandler {

    /** 注册账号时写入当前 UTC 时间。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", () -> OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.class);
    }

    /** 用户表没有需要自动填充的更新字段。 */
    @Override
    public void updateFill(MetaObject metaObject) {
    }
}
