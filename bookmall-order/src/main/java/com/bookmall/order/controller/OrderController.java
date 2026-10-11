package com.bookmall.order.controller;

import com.bookmall.order.service.CartService;
import com.bookmall.order.service.OrderService;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 购物车和订单接口。用户编号一律从登录令牌读取，不接受请求体里的 userId。
 */
@RestController
@RequiredArgsConstructor
public class OrderController {

    /** 购物车。 */
    private final CartService cartService;

    /** 订单。 */
    private final OrderService orderService;

    /**
     * 查看当前用户的购物车。
     *
     * @param request 已登录请求
     * @return 条目和合计
     */
    @GetMapping("/api/cart")
    public ApiResponse<Map<String, Object>> cart(HttpServletRequest request) {
        return ApiResponse.ok(cartService.list(JwtAuthFilter.userId(request)));
    }

    /**
     * 加入购物车。
     *
     * @param request 已登录请求
     * @param body    含 bookId，quantity 可选
     * @return 更新后的购物车行
     */
    @PostMapping("/api/cart/items")
    public ApiResponse<Map<String, Object>> add(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
        return ApiResponse.ok(cartService.add(JwtAuthFilter.userId(request), body));
    }

    /**
     * 修改购物车数量。数量为 0 时删除该行，data 为 null。
     *
     * @param request 已登录请求
     * @param id      购物车条目主键
     * @param body    含 quantity
     * @return 更新后的行，删除时 data 为 null
     */
    @PutMapping("/api/cart/items/{id}")
    public ApiResponse<Map<String, Object>> update(HttpServletRequest request, @PathVariable String id,
                                                   @RequestBody(required = false) JsonNode body) {
        return ApiResponse.ok(cartService.update(JwtAuthFilter.userId(request), parseId(id, "购物车条目不存在"), body));
    }

    /**
     * 删除一条购物车记录。
     *
     * @param request 已登录请求
     * @param id      购物车条目主键
     * @return 空数据的成功响应
     */
    @DeleteMapping("/api/cart/items/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable String id) {
        cartService.delete(JwtAuthFilter.userId(request), parseId(id, "购物车条目不存在"));
        return ApiResponse.ok(null);
    }

    /**
     * 用全部购物车创建订单。
     *
     * @param request 已登录请求
     * @param body    可空；带 itemIds 会被拒绝
     * @return 新订单详情
     */
    @PostMapping("/api/orders")
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody(required = false) JsonNode body) {
        return ApiResponse.ok(orderService.create(JwtAuthFilter.userId(request), body));
    }

    /**
     * 分页查询当前用户的订单。
     *
     * @param request 已登录请求
     * @param page    页码，可空
     * @param size    每页条数，可空
     * @return 订单摘要分页
     */
    @GetMapping("/api/orders")
    public ApiResponse<PageResult<Map<String, Object>>> orders(HttpServletRequest request,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.list(JwtAuthFilter.userId(request), page, size));
    }

    /**
     * 查询一笔订单详情。路径不是正整数时按订单不存在处理。
     *
     * @param request 已登录请求
     * @param id      订单主键
     * @return 订单详情
     */
    @GetMapping("/api/orders/{id}")
    public ApiResponse<Map<String, Object>> order(HttpServletRequest request, @PathVariable String id) {
        return ApiResponse.ok(orderService.detail(JwtAuthFilter.userId(request), parseId(id, "订单不存在")));
    }

    /**
     * 取消本人已创建订单。成功后状态为 CANCELLED，库存已按明细加回。
     */
    @PostMapping("/api/orders/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(HttpServletRequest request, @PathVariable String id) {
        return ApiResponse.ok(orderService.cancel(JwtAuthFilter.userId(request), parseId(id, "订单不存在")));
    }

    /** 路径主键必须是正整数，否则按资源不存在处理。 */
    private static long parseId(String raw, String message) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw BusinessException.notFound(message);
            }
            return id;
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound(message);
        }
    }
}
