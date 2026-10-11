package com.bookmall.notification.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 插入通知时补上创建时间。字段已有值时不会覆盖。
 */
@Component
public class CreatedAtFillHandler implements MetaObjectHandler {

    /** 创建通知时写入当前 UTC 时间。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", () -> OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.class);
    }

    /** 标为已读时不改创建时间。 */
    @Override
    public void updateFill(MetaObject metaObject) {
    }
}
