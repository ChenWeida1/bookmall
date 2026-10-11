package com.bookmall.order;

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
 * 订单服务入口。
 * <p>
 * 负责购物车和下单。购物车、下单、订单查询都要求登录，因此这里没有公开接口。
 * 扫描 {@code com.bookmall} 以加载公共模块的全局异常处理。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
public class OrderApplication {

    /** 启动订单服务。 */
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }

    /**
     * 使用与用户服务相同的密钥校验令牌。
     *
     * @param secret {@code bookmall.jwt.secret}
     * @return JWT 校验器
     */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }

    /**
     * 注册鉴权过滤器。本服务全部接口都需要登录。
     *
     * @param jwtService   令牌校验器
     * @param objectMapper 写出 401 响应体
     * @return 过滤器注册项
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtService, objectMapper, OrderApplication::isPublic));
        bean.setOrder(1);
        return bean;
    }

    /**
     * 订单服务没有匿名接口。
     *
     * @param request 当前请求
     * @return 始终为 false
     */
    static boolean isPublic(HttpServletRequest request) {
        return false;
    }
}
