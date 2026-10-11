package com.bookmall.order.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 插入订单时补上下单时间。购物车和明细没有该字段，填充逻辑会跳过它们。
 */
@Component
public class CreatedAtFillHandler implements MetaObjectHandler {

    /** 下单时写入当前 UTC 时间。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", () -> OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.class);
    }

    /** 订单表没有需要自动填充的更新字段。 */
    @Override
    public void updateFill(MetaObject metaObject) {
    }
}
