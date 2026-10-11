package com.bookmall.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.order.client.CatalogClient;
import com.bookmall.order.entity.CartItem;
import com.bookmall.order.mapper.CartItemMapper;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车的查询、加入、改数量和删除。
 * <p>
 * 同一本书再次加入时与已有数量合并，合并后的数量不能超过当前库存。
 * 数量改为 0 等于删除该行。书已下架或不存在时不能加入，列表里若书已删除则用占位信息展示。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    /** 购物车表。 */
    private final CartItemMapper carts;

    /** 目录服务客户端。 */
    private final CatalogClient catalog;

    /**
     * 列出当前用户的购物车，并按实时单价计算合计。
     *
     * @param userId 当前用户
     * @return items 与 totalAmount
     */
    public Map<String, Object> list(long userId) {
        List<Map<String, Object>> items = carts.selectList(Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .orderByAsc(CartItem::getId)).stream()
                .map(this::line)
                .toList();
        BigDecimal total = items.stream()
                .map(item -> new BigDecimal(String.valueOf(item.get("subtotal"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("totalAmount", Texts.money(total));
        return data;
    }

    /**
     * 把一本书加入购物车。未传数量时按 1 本。已有同一本书则累加。
     * <p>
     * 合并用数据库唯一约束完成。超过库存时回滚这一次累加，已提交的数量保持不变。
     *
     * @param userId 当前用户
     * @param body   含 bookId，quantity 可选
     * @return 更新后的这一行
     */
    @Transactional(propagation = Propagation.NESTED)
    public Map<String, Object> add(long userId, JsonNode body) {
        if (body == null || !body.hasNonNull("bookId")) {
            throw BusinessException.badRequest("参数不合法");
        }
        long bookId = positiveLong(body.get("bookId"), "参数不合法");
        int quantity = body.has("quantity") && !body.get("quantity").isNull()
                ? positiveInt(body.get("quantity"))
                : 1;
        CatalogClient.BookSnapshot book = requireOnSale(catalog.getBook(bookId));
        CartItem item = carts.addQuantity(userId, bookId, quantity);
        ensureStock(book, item.getQuantity());
        return line(item, book);
    }

    /**
     * 把某一行改成指定数量。数量为 0 时删除该行并返回 null。
     *
     * @param userId 当前用户
     * @param itemId 购物车条目主键
     * @param body   含 quantity，允许 0
     * @return 更新后的行；删除时为 null
     */
    @Transactional
    public Map<String, Object> update(long userId, long itemId, JsonNode body) {
        if (body == null || !body.has("quantity") || body.get("quantity").isNull()) {
            throw new BusinessException(400, 40007, "数量不合法");
        }
        int quantity = nonNegativeInt(body.get("quantity"));
        CartItem item = findOwned(userId, itemId);
        if (quantity == 0) {
            carts.deleteById(item.getId());
            return null;
        }
        CatalogClient.BookSnapshot book = requireOnSale(catalog.getBook(item.getBookId()));
        ensureStock(book, quantity);
        item.setQuantity(quantity);
        carts.updateById(item);
        return line(item, book);
    }

    /**
     * 删除当前用户的一条购物车记录。不属于该用户时按不存在处理。
     *
     * @param userId 当前用户
     * @param itemId 购物车条目主键
     */
    @Transactional
    public void delete(long userId, long itemId) {
        CartItem item = findOwned(userId, itemId);
        carts.deleteById(item.getId());
    }

    /** 按条目主键和用户一起查找，防止越权修改。 */
    private CartItem findOwned(long userId, long itemId) {
        CartItem item = carts.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw BusinessException.notFound("购物车条目不存在");
        }
        return item;
    }

    /** 列表展示时重新查书。书已被删则用零价占位，避免整车无法打开。 */
    private Map<String, Object> line(CartItem item) {
        CatalogClient.BookSnapshot book = catalog.getBook(item.getBookId());
        if (!book.exists()) {
            return line(item, new CatalogClient.BookSnapshot(item.getBookId(), false, "图书不存在", null, BigDecimal.ZERO, 0, "OFF_SALE"));
        }
        return line(item, book);
    }

    /** 把购物车行和图书快照拼成接口字段，小计为单价乘数量。 */
    private Map<String, Object> line(CartItem item, CatalogClient.BookSnapshot book) {
        BigDecimal subtotal = Texts.times(book.price(), item.getQuantity());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("bookId", item.getBookId());
        row.put("bookTitle", book.title());
        row.put("coverUrl", book.coverUrl());
        row.put("unitPrice", Texts.money(book.price()));
        row.put("quantity", item.getQuantity());
        row.put("subtotal", Texts.money(subtotal));
        return row;
    }

    /** 加入和改数量都要求图书存在且在售。 */
    private static CatalogClient.BookSnapshot requireOnSale(CatalogClient.BookSnapshot book) {
        if (!book.exists()) {
            throw BusinessException.notFound("图书不存在");
        }
        if (!"ON_SALE".equals(book.status())) {
            throw new BusinessException(400, 40004, "图书未上架");
        }
        return book;
    }

    /** 目标数量超过库存时返回 40006，并带上书名、库存和请求数量。 */
    private static void ensureStock(CatalogClient.BookSnapshot book, int quantity) {
        if (quantity > book.stock()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookId", book.bookId());
            data.put("bookTitle", book.title());
            data.put("stock", book.stock());
            data.put("quantity", quantity);
            throw new BusinessException(400, 40006, "库存不足", data);
        }
    }

    /**
     * 读取必须为正整数的 JSON 数字。小数和小于等于 0 都算参数不合法。
     *
     * @param node    JSON 节点
     * @param message 失败时的说明
     * @return 正整数
     */
    static long positiveLong(JsonNode node, String message) {
        if (!node.isIntegralNumber() || node.longValue() <= 0 || node.isFloatingPointNumber()) {
            throw BusinessException.badRequest(message);
        }
        return node.longValue();
    }

    /** 读取大于 0 的整数数量。 */
    static int positiveInt(JsonNode node) {
        int value = nonNegativeInt(node);
        if (value <= 0) {
            throw new BusinessException(400, 40007, "数量不合法");
        }
        return value;
    }

    /** 读取大于等于 0 的整数。小数、负数和非数字都按数量不合法处理。 */
    static int nonNegativeInt(JsonNode node) {
        if (node.isFloatingPointNumber() || !node.isIntegralNumber() || node.intValue() < 0) {
            throw new BusinessException(400, 40007, "数量不合法");
        }
        return node.intValue();
    }
}
