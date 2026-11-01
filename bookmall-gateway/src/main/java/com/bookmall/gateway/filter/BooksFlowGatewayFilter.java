package com.bookmall.gateway.filter;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 图书列表的漏桶式匀速排队。
 * <p>
 * 资源名 {@code GET:/api/books}，规则 {@code controlBehavior=2}：按目标 QPS 排队放行，
 * 超过 {@code maxQueueingTimeMs} 再拒绝。只匹配精确路径，不拦详情 {@code /api/books/{id}}。
 * Entry 在下游响应结束后再 exit，避免响应式链路上统计不准。
 */
@Component
@RequiredArgsConstructor
public class BooksFlowGatewayFilter implements GlobalFilter, Ordered {

    static final String RESOURCE = "GET:/api/books";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())
                || !"/api/books".equals(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        final Entry entry;
        try {
            entry = SphU.entry(RESOURCE);
        } catch (BlockException ex) {
            return RateLimitResponses.tooManyRequests(exchange, objectMapper);
        }
        return chain.filter(exchange).doFinally(signal -> entry.exit());
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
