package com.bookmall.assistant.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.bookmall.common.error.BusinessException;

import java.util.function.Supplier;

/**
 * 客服提问在写出 SSE 之前做 QPS 硬拒绝。
 */
public final class SentinelCalls {

    public static final String ASSISTANT_ASK = "assistant-ask";

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
