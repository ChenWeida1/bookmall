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
     * 处理最早的一条超时死信。请求体 {@code action} 为 replay 或 discard。
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
