package com.bookmall.catalog;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.bookmall.catalog.baike.HttpBaikeClient;
import com.bookmall.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 不启 Spring 上下文：直接验证熔断打开后 {@link HttpBaikeClient} 映射为 50302。
 */
class BaikeLookupDegradeTest {

    @AfterEach
    void clear() {
        DegradeRuleManager.loadRules(List.of());
    }

    @Test
    void openCircuitMapsTo50302() {
        DegradeRule rule = new DegradeRule(HttpBaikeClient.RESOURCE);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(0.1);
        rule.setTimeWindow(30);
        rule.setMinRequestAmount(1);
        rule.setStatIntervalMs(1000);
        DegradeRuleManager.loadRules(List.of(rule));

        // 先制造失败样本，再触发熔断。客户端指向不可达地址。
        com.bookmall.catalog.config.BaikeProperties props = new com.bookmall.catalog.config.BaikeProperties();
        props.setAppId("1");
        props.setCardUrl("http://127.0.0.1:1/missing");
        props.setTimeout(java.time.Duration.ofMillis(50));
        props.setExcerptLength(100);
        HttpBaikeClient client = new HttpBaikeClient(props);

        for (int i = 0; i < 8; i++) {
            try {
                client.lookup("围城");
            } catch (BusinessException ignored) {
                // 50302 或熔断后的 50302
            }
        }
        BusinessException ex = assertThrows(BusinessException.class, () -> client.lookup("围城"));
        assertEquals(503, ex.getHttpStatus());
        assertEquals(50302, ex.getCode());
    }
}
