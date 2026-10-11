package com.bookmall.user;

import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.auth.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户服务入口。
 * <p>
 * 负责注册、登录和当前用户查询。扫描 {@code com.bookmall} 以便加载公共模块里的全局异常处理。
 * 注册和登录是公开接口，其余请求由 {@link JwtAuthFilter} 校验令牌。
 */
@SpringBootApplication(scanBasePackages = "com.bookmall")
public class UserApplication {

    /** 启动用户服务。 */
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }

    /**
     * 使用配置中的共享密钥创建令牌服务。
     *
     * @param secret {@code bookmall.jwt.secret}
     * @return JWT 签发与校验器
     */
    @Bean
    JwtService jwtService(@Value("${bookmall.jwt.secret}") String secret) {
        return new JwtService(secret);
    }

    /**
     * 密码编码器。注册时哈希密码，登录时只做匹配，不保存明文。
     *
     * @return BCrypt 编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 注册鉴权过滤器，顺序靠前，使未登录请求进不了控制器。
     *
     * @param jwtService   令牌校验器
     * @param objectMapper 写出 401 响应体
     * @return 过滤器注册项
     */
    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtAuthFilter(jwtService, objectMapper, UserApplication::isPublic));
        bean.setOrder(1);
        return bean;
    }

    /**
     * 只有注册和登录的 POST 不需要令牌。
     *
     * @param request 当前请求
     * @return 是否为公开接口
     */
    static boolean isPublic(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/users/register".equals(path) || "/api/users/login".equals(path);
    }
}
