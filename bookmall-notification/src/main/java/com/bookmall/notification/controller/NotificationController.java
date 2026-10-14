package com.bookmall.notification.controller;

import com.bookmall.notification.service.NotificationService;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知接口。
 * <p>
 * {@code /api/notifications} 给已登录用户查看和标已读。
 * {@code /internal/notifications} 给订单服务创建通知，网关不把这条路径暴露到外网。
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    /** 通知逻辑。 */
    private final NotificationService notificationService;

    /**
     * 分页查询当前用户的通知。
     *
     * @param request 已登录请求
     * @param page    页码，可空
     * @param size    每页条数，可空
     * @return 通知分页
     */
    @GetMapping("/api/notifications")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notificationService.list(JwtAuthFilter.userId(request), page, size));
    }

    /**
     * 把一条通知标为已读。路径不是正整数时按通知不存在处理。
     *
     * @param request 已登录请求
     * @param id      通知主键
     * @return 更新后的通知
     */
    @PostMapping("/api/notifications/{id}/read")
    public ApiResponse<Map<String, Object>> read(HttpServletRequest request, @PathVariable String id) {
        return ApiResponse.ok(notificationService.markRead(JwtAuthFilter.userId(request), parseId(id)));
    }

    /**
     * 内部创建通知。调用方是订单服务，不从令牌取用户。
     *
     * @param body 含 userId、title、content
     * @return 新建通知
     */
    /**
     * 内部创建/补发通知。可选 bizKey 用于幂等。
     * 主路径已改为 MQ 消费；本接口保留给运维补发。
     */
    @PostMapping("/internal/notifications")
    public ApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        Long userId = longValue(json.get("userId"));
        String bizKey = text(json.get("bizKey"));
        if (bizKey == null) {
            bizKey = text(json.get("biz_key"));
        }
        return ApiResponse.ok(notificationService.create(
                userId, text(json.get("title")), text(json.get("content")), bizKey));
    }

    /** 路径主键必须是正整数。 */
    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw BusinessException.notFound("通知不存在");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound("通知不存在");
        }
    }

    /** 把 JSON 数字或数字字符串转成 Long。小数截断会通知错用户，无法解析时返回 null，交给服务层报参数错误。 */
    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!Double.isFinite(raw) || raw != Math.floor(raw) || raw <= 0) {
                return null;
            }
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 把 JSON 值转成字符串，缺失时为 null。 */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
