package com.bookmall.gateway.filter;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooksFlowGatewayFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void loadTightRule() {
        FlowRule rule = new FlowRule(BooksFlowGatewayFilter.RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        rule.setMaxQueueingTimeMs(10);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void excessListTrafficReturns42901() throws Exception {
        BooksFlowGatewayFilter filter = new BooksFlowGatewayFilter(objectMapper);
        AtomicInteger passed = new AtomicInteger();
        GatewayFilterChain pass = exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        };
        int blocked = 0;
        for (int i = 0; i < 30; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/books").build());
            filter.filter(exchange, pass).block();
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                blocked++;
            }
        }
        assertTrue(passed.get() >= 1);
        assertTrue(blocked >= 1);
    }

    @Test
    void bookDetailIsNotLimited() {
        BooksFlowGatewayFilter filter = new BooksFlowGatewayFilter(objectMapper);
        AtomicInteger passed = new AtomicInteger();
        GatewayFilterChain pass = exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        };
        for (int i = 0; i < 10; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/books/1").build());
            filter.filter(exchange, pass).block();
            assertEquals(null, exchange.getResponse().getStatusCode());
        }
        assertEquals(10, passed.get());
    }
}
