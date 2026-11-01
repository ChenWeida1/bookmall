package com.bookmall.assistant;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.bookmall.assistant.sentinel.SentinelCalls;
import com.bookmall.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistantAskRateLimitTest {

    @BeforeEach
    void tightRule() {
        FlowRule rule = new FlowRule(SentinelCalls.ASSISTANT_ASK);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @AfterEach
    void restore() {
        FlowRule rule = new FlowRule(SentinelCalls.ASSISTANT_ASK);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(3);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @Test
    void askBlockedReturns42901BeforeAction() {
        AtomicBoolean ran = new AtomicBoolean();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SentinelCalls.guard(SentinelCalls.ASSISTANT_ASK, () -> {
                    ran.set(true);
                    return null;
                }));
        assertFalse(ran.get());
        assertEquals(429, ex.getHttpStatus());
        assertEquals(42901, ex.getCode());
        assertEquals("请求过于频繁", ex.getMessage());
    }
}
