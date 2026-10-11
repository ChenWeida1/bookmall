package com.bookmall.gateway.filter;

import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权。
 * <p>
 * 内部接口一律返回 404，避免订单服务的扣库存和写通知被外部直接调用。
 * 注册、登录，以及分类和图书的 GET 可以匿名访问。其余请求必须携带有效的 Bearer 令牌。
 * 过滤器顺序为 -100，赶在路由转发之前执行。
 */
@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    /** 令牌校验器。 */
    private final JwtService jwtService;

    /** 写出未登录 JSON。 */
    private final ObjectMapper objectMapper;

    /**
     * 拦截内部路径，放行公开接口和预检请求，其余请求校验 JWT。
     *
     * @param exchange 当前请求和响应
     * @param chain    后续过滤器
     * @return 放行或直接结束响应
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/internal/") || "/internal".equals(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || isPublic(exchange, path)) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        try {
            jwtService.parse(header.substring(7).trim());
            return chain.filter(exchange);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange);
        }
    }

    /**
     * 匿名可访问的接口：注册、登录，以及分类和图书查询。
     * 图书详情用前缀匹配，列表和分类用精确路径。
     */
    private boolean isPublic(ServerWebExchange exchange, String path) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.POST.equals(method)
                && ("/api/users/register".equals(path) || "/api/users/login".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method)) {
            return "/api/categories".equals(path)
                    || "/api/books".equals(path)
                    || path.startsWith("/api/books/");
        }
        return false;
    }

    /** 写出与业务服务一致的 401 响应。序列化失败时退回一段固定 JSON。 */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.fail(40100, "未登录或登录已失效"));
        } catch (Exception ex) {
            body = "{\"code\":40100,\"message\":\"未登录或登录已失效\",\"data\":null}".getBytes();
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 比默认路由过滤器更早执行，使未登录请求不会被转发到下游。
     *
     * @return -100
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
