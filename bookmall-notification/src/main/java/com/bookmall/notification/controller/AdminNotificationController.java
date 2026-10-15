package com.bookmall.notification.controller;

import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import com.bookmall.notification.service.NotificationDeadLetterService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员查看、重投或丢弃通知死信。普通读者返回 40300。
 */
@RestController
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationDeadLetterService deadLetters;

    @GetMapping("/api/admin/notifications/dead-letters")
    public ApiResponse<Map<String, Object>> deadLetters(HttpServletRequest request) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(deadLetters.inspect());
    }

    @PostMapping("/api/admin/notifications/dead-letters")
    public ApiResponse<Map<String, Object>> deadLetterAction(HttpServletRequest request,
                                                             @RequestBody(required = false) JsonNode body) {
        JwtAuthFilter.requireAdmin(request);
        if (body == null || !body.hasNonNull("action")) {
            throw BusinessException.badRequest("参数不合法");
        }
        return ApiResponse.ok(deadLetters.act(body.get("action").asText()));
    }
}
