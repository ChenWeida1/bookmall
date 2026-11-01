package com.bookmall.order.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.bookmall.common.error.BusinessException;

import java.util.function.Supplier;

/**
 * 在事务开启前用手写入境做 QPS 硬拒绝，避免 {@code @SentinelResource} 落在 {@code @Transactional} 内侧。
 */
public final class SentinelCalls {

    public static final String ORDER_CREATE = "order-create";

    private SentinelCalls() {
    }

    public static <T> T guard(String resource, Supplier<T> action) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            return action.get();
        } catch (BlockException ex) {
            throw BusinessException.tooManyRequests();
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }
}
