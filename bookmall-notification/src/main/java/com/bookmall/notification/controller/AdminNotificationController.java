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
 * 通知写库失败、消息进了死信队列 {@code bookmall.notification.create.dlq} 之后，给管理员补救用。
 * 这个队列没有常驻消费者，消息会一直停在里面，直到这里被查看或处理。
 * 普通读者调用返回 HTTP 403、{@code code} 40300。
 */
@RestController
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationDeadLetterService deadLetters;

    /**
     * 只看不取。返回队列名、积压条数，以及队头那一条的内容（空队列时 {@code head} 为 null）。
     * 队头会被读出来再放回原位，这条消息还在队列里，刷新页面看到的仍是同一条。
     */
    @GetMapping("/api/admin/notifications/dead-letters")
    public ApiResponse<Map<String, Object>> deadLetters(HttpServletRequest request) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(deadLetters.inspect());
    }

    /**
     * 处理队头最早的一条，请求体是 {@code {"action":"replay"}} 或 {@code {"action":"discard"}}。
     * replay：把这条消息发回交换机 {@code bookmall.notification}、路由键 {@code notification.create}，
     * 由原来的消费者再写一次通知；同一条 {@code bizKey} 已经入库过就不会重复插。
     * discard：直接确认掉，消息从死信队列消失，不再写通知。
     * 没传 action、action 不是这两个值，或队列是空的，返回 40001。
     */
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
