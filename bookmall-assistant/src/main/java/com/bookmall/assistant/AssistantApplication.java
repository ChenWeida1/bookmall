package com.bookmall.assistant;

import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 查书客服入口。
 * <p>
 * 只回答图书查询。下单、支付、取消不经过这里。
 * 全部接口都要登录，扫描 {@code com.bookmall} 以加载统一异常处理。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
@EnableFeignClients(basePackages = "com.bookmall.assistant.client")
public class AssistantApplication {

    /** 启动客服服务。 */
    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }

    /** 与其他服务共用同一套 JWT 密钥。 */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }

    /** 本服务没有匿名接口。 */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtService, objectMapper, AssistantApplication::isPublic));
        bean.setOrder(1);
        return bean;
    }

    /** 客服接口一律要登录。 */
    static boolean isPublic(HttpServletRequest request) {
        return false;
    }
}
