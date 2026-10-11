package com.bookmall.catalog;

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
 * 图书目录服务入口。
 * <p>
 * 提供分类、图书查询，管理端上下架，以及给订单服务调用的库存扣减和回补。
 * 扫描 {@code com.bookmall} 以加载公共模块的全局异常处理。
 * GET 目录与内部库存接口公开；{@code /api/admin/**} 需要管理员 JWT。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
public class CatalogApplication {

    /** 启动目录服务。 */
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }

    /** 与网关、用户服务共用同一 JWT 密钥，校验管理端令牌。 */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }

    /** 注册鉴权过滤器：公开接口放行，管理端必须带令牌。 */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtService, objectMapper, CatalogApplication::isPublic));
        bean.setOrder(1);
        return bean;
    }

    /**
     * 公开：GET /api/categories、/api/books、/api/books/*，以及全部 /internal/**。
     * 管理端 PUT /api/admin/** 不公开。
     */
    static boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/") || "/internal".equals(path)) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return "/api/categories".equals(path)
                || "/api/books".equals(path)
                || path.startsWith("/api/books/");
    }
}
