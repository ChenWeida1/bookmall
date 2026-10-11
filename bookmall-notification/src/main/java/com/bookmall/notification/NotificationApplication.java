package com.bookmall.notification;

import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * 通知服务入口。
 * <p>
 * 用户查看和标记已读需要登录。订单服务调用的内部创建接口不走用户令牌，
 * 由网关挡住外部对 {@code /internal} 的访问。扫描 {@code com.bookmall} 以加载公共异常处理。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
public class NotificationApplication {

    /** 启动通知服务。 */
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }

    /**
     * 使用共享密钥校验用户令牌。
     *
     * @param secret {@code bookmall.jwt.secret}
     * @return JWT 校验器
     */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }

    /**
     * 注册鉴权过滤器。{@code /internal/} 开头的内部接口不要求用户登录。
     *
     * @param jwtService   令牌校验器
     * @param objectMapper 写出 401 响应体
     * @return 过滤器注册项
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtService, objectMapper, NotificationApplication::isPublic));
        bean.setOrder(1);
        return bean;
    }

    /**
     * 内部写通知接口由订单服务直连，不携带用户令牌。
     *
     * @param request 当前请求
     * @return 路径以 {@code /internal/} 开头时为 true
     */
    static boolean isPublic(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/internal/");
    }
}
