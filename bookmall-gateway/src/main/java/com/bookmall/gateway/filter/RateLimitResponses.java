package com.bookmall.gateway.filter;

import com.bookmall.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关限流统一写出 {@code 42901}。
 */
final class RateLimitResponses {

    private RateLimitResponses() {
    }

    static Mono<Void> tooManyRequests(ServerWebExchange exchange, ObjectMapper objectMapper) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.fail(42901, "请求过于频繁"));
        } catch (Exception ex) {
            body = "{\"code\":42901,\"message\":\"请求过于频繁\",\"data\":null}".getBytes();
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
