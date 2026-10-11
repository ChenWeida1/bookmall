package com.bookmall.common.auth;

import com.bookmall.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * 业务服务侧的 JWT 鉴权过滤器。
 * <p>
 * 网关已经拦过一次未登录请求。服务自身再校验，是为了直连端口时仍能拒绝匿名访问。
 * CORS 预检和各服务声明的公开接口直接放行。其余请求必须带 {@code Authorization: Bearer}，
 * 解析成功后把用户编号和登录名放进请求属性。
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 请求属性名：当前用户主键。 */
    public static final String USER_ID = "bookmall.userId";

    /** 请求属性名：当前登录名。 */
    public static final String USERNAME = "bookmall.username";

    /** 令牌校验器。 */
    private final JwtService jwtService;

    /** 写出未登录 JSON。 */
    private final ObjectMapper objectMapper;

    /** 由各服务提供，返回 true 表示该请求无需令牌。 */
    private final Predicate<HttpServletRequest> isPublic;

    /**
     * 放行公开请求；其余请求校验 Bearer 令牌，失败时直接返回 401，不再进入控制器。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic.test(request)) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }
        try {
            JwtUser user = jwtService.parse(header.substring(7).trim());
            request.setAttribute(USER_ID, user.userId());
            request.setAttribute(USERNAME, user.username());
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response);
        }
    }

    /** 以统一响应体写出未登录。 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(40100, "未登录或登录已失效"));
    }

    /**
     * 读取过滤器写入的当前用户编号。
     *
     * @param request 已通过鉴权的请求
     * @return 用户主键
     * @throws IllegalStateException 过滤器未写入用户，通常表示该接口被错误地标成公开
     */
    public static long userId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw new IllegalStateException("缺少登录用户");
    }
}
