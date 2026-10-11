package com.bookmall.user.controller;

import com.bookmall.user.service.AuthService;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户相关接口：注册、登录、当前用户。
 * <p>
 * 请求体用 Map 接收，缺字段时交给 {@link AuthService} 按业务规则报错，
 * 而不是让框架在绑定阶段直接返回默认的 400。
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    /** 注册与登录逻辑。 */
    private final AuthService authService;

    /**
     * 注册。成功后返回令牌，前端可直接进入已登录状态。
     *
     * @param body 含 username、password，nickname 可选
     * @return 令牌和用户摘要
     */
    @PostMapping("/api/users/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        return ApiResponse.ok(authService.register(
                text(json.get("username")),
                raw(json.get("password")),
                text(json.get("nickname"))));
    }

    /**
     * 登录。用户名不存在和密码错误使用同一错误码。
     *
     * @param body 含 username、password
     * @return 令牌和用户摘要
     */
    @PostMapping("/api/users/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        return ApiResponse.ok(authService.login(text(json.get("username")), raw(json.get("password"))));
    }

    /**
     * 读取当前登录用户。用户编号来自鉴权过滤器，不接受客户端传入。
     *
     * @param request 已通过 JWT 校验的请求
     * @return 用户摘要
     */
    @GetMapping("/api/users/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.me(JwtAuthFilter.userId(request)));
    }

    /** 把 JSON 值转成字符串；缺失时返回 null，交给服务层判断。 */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 密码必须出现在请求体中，缺失时按用户名和密码不能为空处理。 */
    private static String raw(Object value) {
        if (value == null) {
            throw BusinessException.badRequest("用户名和密码不能为空");
        }
        return String.valueOf(value);
    }
}
