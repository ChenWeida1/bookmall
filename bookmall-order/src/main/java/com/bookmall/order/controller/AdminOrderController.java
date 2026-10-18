package com.bookmall.order.controller;

import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import com.bookmall.order.service.OrderService;
import com.bookmall.order.service.PayTimeoutDeadLetterService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员只读订单，以及支付超时死信的查看、重投和丢弃。
 * 不提供管理员支付或取消。
 */
@RestController
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final PayTimeoutDeadLetterService deadLetters;

    /** 分页查看全部用户的订单摘要。 */
    @GetMapping("/api/admin/orders")
    public ApiResponse<PageResult<Map<String, Object>>> orders(HttpServletRequest request,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size,
                                                               @RequestParam(required = false) String status) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(orderService.adminList(page, size, status));
    }

    /**
     * 查看超时死信队列积压。路径必须写在 {@code /{id}} 前面的独立映射上，
     * 避免被订单主键吞掉。
     */
    @GetMapping("/api/admin/orders/dead-letters")
    public ApiResponse<Map<String, Object>> deadLetters(HttpServletRequest request) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(deadLetters.inspect());
    }

    /**
     * 处理支付超时死信队列 {@code bookmall.order.pay.timeout.dlq} 里最早的一条。
     * 请求体是 {@code {"action":"replay"}} 或 {@code {"action":"discard"}}。
     * replay：发回交换机 {@code bookmall.order.pay}、路由键 {@code pay.timeout}，
     * 直接进入超时队列，不再进延迟队列重等。消费者只在订单仍是待支付时取消并还库存。
     * discard：确认掉这条消息，订单和库存都不动。
     * 没传 action、action 不是这两个值，或队列是空的，返回 40001。
     */
    @PostMapping("/api/admin/orders/dead-letters")
    public ApiResponse<Map<String, Object>> deadLetterAction(HttpServletRequest request,
                                                             @RequestBody(required = false) JsonNode body) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(deadLetters.act(actionOf(body)));
    }

    /** 任意用户的订单详情。 */
    @GetMapping("/api/admin/orders/{id}")
    public ApiResponse<Map<String, Object>> order(HttpServletRequest request, @PathVariable String id) {
        JwtAuthFilter.requireAdmin(request);
        return ApiResponse.ok(orderService.adminDetail(parseId(id)));
    }

    private static String actionOf(JsonNode body) {
        if (body == null || !body.hasNonNull("action")) {
            throw BusinessException.badRequest("参数不合法");
        }
        return body.get("action").asText();
    }

    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw BusinessException.notFound("订单不存在");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound("订单不存在");
        }
    }
}
