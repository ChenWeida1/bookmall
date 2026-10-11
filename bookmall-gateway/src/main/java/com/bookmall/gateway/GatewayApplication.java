package com.bookmall.gateway;

import com.bookmall.common.auth.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 网关入口。前端只访问这里，由网关把 {@code /api} 转发到用户、目录、订单和通知服务。
 */
@SpringBootApplication
public class GatewayApplication {

    /** 启动网关。 */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * 供全局过滤器校验令牌。密钥必须与用户服务签发时使用的密钥相同。
     *
     * @param secret {@code bookmall.jwt.secret}
     * @return JWT 校验器
     */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }
}
