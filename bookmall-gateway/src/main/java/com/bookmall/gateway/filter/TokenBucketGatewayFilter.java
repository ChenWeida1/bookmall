package com.bookmall.gateway.filter;

import com.bookmall.gateway.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 登录与注册的令牌桶限流。
 * <p>
 * 用 Guava {@link RateLimiter} 实现经典令牌桶：平均速率受控，短突发可消耗积攒的令牌。
 * 与图书列表的 Sentinel 匀速排队对照讲解。过滤器顺序在 JWT 鉴权之后。
 */
@Component
@RequiredArgsConstructor
public class TokenBucketGatewayFilter implements GlobalFilter, Ordered {

    private final RateLimitProperties rates;

    private final ObjectMapper objectMapper;

    private volatile RateLimiter login;

    private volatile RateLimiter register;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        if ("/api/users/login".equals(path)) {
            if (!limiter(true).tryAcquire()) {
                return RateLimitResponses.tooManyRequests(exchange, objectMapper);
            }
        } else if ("/api/users/register".equals(path)) {
            if (!limiter(false).tryAcquire()) {
                return RateLimitResponses.tooManyRequests(exchange, objectMapper);
            }
        }
        return chain.filter(exchange);
    }

    private RateLimiter limiter(boolean forLogin) {
        if (forLogin) {
            RateLimiter current = login;
            if (current == null) {
                synchronized (this) {
                    if (login == null) {
                        login = RateLimiter.create(Math.max(rates.getLoginRate(), 0.1));
                    }
                    return login;
                }
            }
            return current;
        }
        RateLimiter current = register;
        if (current == null) {
            synchronized (this) {
                if (register == null) {
                    register = RateLimiter.create(Math.max(rates.getRegisterRate(), 0.1));
                }
                return register;
            }
        }
        return current;
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
