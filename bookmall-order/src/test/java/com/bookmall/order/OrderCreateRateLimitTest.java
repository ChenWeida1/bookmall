package com.bookmall.order;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.bookmall.common.error.BusinessException;
import com.bookmall.order.sentinel.SentinelCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreateRateLimitTest {

    @BeforeEach
    void tightRule() {
        FlowRule rule = new FlowRule(SentinelCalls.ORDER_CREATE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @AfterEach
    void restore() {
        FlowRule rule = new FlowRule(SentinelCalls.ORDER_CREATE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(5);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @Test
    void createBlockedReturns42901WithoutCallingAction() {
        AtomicBoolean ran = new AtomicBoolean();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SentinelCalls.guard(SentinelCalls.ORDER_CREATE, () -> {
                    ran.set(true);
                    return "ok";
                }));
        assertFalse(ran.get());
        assertEquals(429, ex.getHttpStatus());
        assertEquals(42901, ex.getCode());
        assertEquals("请求过于频繁", ex.getMessage());
    }
}
