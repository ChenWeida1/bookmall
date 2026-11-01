package com.bookmall.gateway.filter;

import com.bookmall.gateway.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketGatewayFilterTest {

    @Test
    void loginBurstEventuallyReturns42901() {
        RateLimitProperties rates = new RateLimitProperties();
        rates.setLoginRate(1.0);
        rates.setRegisterRate(1.0);
        TokenBucketGatewayFilter filter = new TokenBucketGatewayFilter(rates, new ObjectMapper());
        AtomicInteger passed = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        GatewayFilterChain pass = exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        };
        for (int i = 0; i < 20; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/users/login").build());
            filter.filter(exchange, pass).block();
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                blocked.incrementAndGet();
            }
        }
        assertTrue(passed.get() >= 1);
        assertTrue(blocked.get() >= 1);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, lastBlockedStatus(filter, pass));
    }

    @Test
    void otherPathsAreNotLimited() {
        RateLimitProperties rates = new RateLimitProperties();
        rates.setLoginRate(0.1);
        TokenBucketGatewayFilter filter = new TokenBucketGatewayFilter(rates, new ObjectMapper());
        AtomicInteger passed = new AtomicInteger();
        GatewayFilterChain pass = exchange -> {
            passed.incrementAndGet();
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/books").build());
        filter.filter(exchange, pass).block();
        assertEquals(1, passed.get());
        assertEquals(null, exchange.getResponse().getStatusCode());
    }

    private static HttpStatus lastBlockedStatus(TokenBucketGatewayFilter filter, GatewayFilterChain pass) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/users/login").build());
        filter.filter(exchange, pass).block();
        return HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }
}
