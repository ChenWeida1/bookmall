package com.bookmall.catalog.controller;

import com.bookmall.catalog.service.CatalogService;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.auth.JwtAuthFilter;
import com.bookmall.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 图书目录接口。
 * <p>
 * {@code /api} 给前端查询分类和图书；{@code /api/admin} 给管理员改上下架。
 * {@code /internal} 只给订单服务扣减和回补库存，网关会拦截外部访问。
 */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    /** 目录与库存逻辑。 */
    private final CatalogService catalogService;

    /**
     * 列出全部分类。
     *
     * @return 分类列表
     */
    @GetMapping("/api/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(catalogService.listCategories());
    }

    /**
     * 分页搜索在售图书。
     *
     * @param page       页码，可空
     * @param size       每页条数，可空
     * @param categoryId 分类主键，可空
     * @param keyword    书名关键词，可空
     * @return 分页结果
     */
    @GetMapping("/api/books")
    public ApiResponse<PageResult<Map<String, Object>>> books(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(catalogService.listBooks(page, size, categoryId, keyword));
    }

    /**
     * 查询图书详情。路径不是正整数时按图书不存在处理。
     *
     * @param id 路径中的图书主键
     * @return 图书详情
     */
    @GetMapping("/api/books/{id}")
    public ApiResponse<Map<String, Object>> book(@PathVariable String id) {
        return ApiResponse.ok(catalogService.detail(parseId(id)));
    }

    /**
     * 管理员修改上架状态，可选覆盖库存。必须 JWT 角色为管理员。
     */
    @PutMapping("/api/admin/books/{id}")
    public ApiResponse<Map<String, Object>> adminUpdate(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        JwtAuthFilter.requireAdmin(request);
        Map<String, Object> json = body == null ? Map.of() : body;
        Object status = json.get("status");
        Object stock = json.get("stock");
        if (status == null && stock == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        return ApiResponse.ok(catalogService.adminUpdate(
                parseId(id),
                status == null ? null : String.valueOf(status),
                stock == null ? null : adminStock(stock)));
    }

    /**
     * 扣减库存，由订单服务在下单时调用。
     *
     * @param id   图书主键
     * @param body 含 quantity 和 token
     * @return 扣减后的库存
     */
    @PostMapping("/internal/books/{id}/stock/deduct")
    public ApiResponse<Map<String, Object>> deduct(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        return ApiResponse.ok(catalogService.deduct(parseId(id), intValue(json.get("quantity")), text(json.get("token"))));
    }

    /**
     * 按 token 回补库存，由订单服务在下单失败或恢复中断占用时调用。
     *
     * @param id   图书主键
     * @param body 含 quantity 和 token
     * @return 空数据的成功响应
     */
    @PostMapping("/internal/books/{id}/stock/restore")
    public ApiResponse<Void> restore(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        catalogService.restore(parseId(id), intValue(json.get("quantity")), text(json.get("token")));
        return ApiResponse.ok(null);
    }

    /**
     * 按数量还库存（订单取消），不依赖扣减令牌。
     */
    @PostMapping("/internal/books/{id}/stock/restore-qty")
    public ApiResponse<Void> restoreQty(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> json = body == null ? Map.of() : body;
        catalogService.restoreQuantity(parseId(id), intValue(json.get("quantity")));
        return ApiResponse.ok(null);
    }

    /** 路径主键必须是正整数，否则视为图书不存在。 */
    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw BusinessException.notFound("图书不存在");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound("图书不存在");
        }
    }

    /** 把 JSON 值转成字符串，缺失时为 null。 */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 把 JSON 数字或数字字符串转成整数。小数和超出 int 范围按数量不合法处理，避免把 1.5 截成 1。 */
    private static Integer intValue(Object value) {
        return wholeNumber(value, new BusinessException(400, 40007, "数量不合法"));
    }

    /** 管理端库存。非法数字按参数不合法，不使用扣库存的 40007。 */
    private static Integer adminStock(Object value) {
        return wholeNumber(value, BusinessException.badRequest("参数不合法"));
    }

    /** 只接受能落在 int 里的整数。{@code 2.0} 可以，{@code 1.5} 不行。 */
    private static Integer wholeNumber(Object value, BusinessException invalid) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer number) {
            return number;
        }
        if (value instanceof Long number) {
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw invalid;
            }
            return number.intValue();
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!Double.isFinite(raw) || raw != Math.floor(raw)
                    || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
                throw invalid;
            }
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw invalid;
        }
    }
}
