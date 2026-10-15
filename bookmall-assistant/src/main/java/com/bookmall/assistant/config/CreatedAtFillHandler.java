package com.bookmall.assistant.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 插入消息时写入当前 UTC 时间。 */
@Component
public class CreatedAtFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", () -> OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
    }
}
