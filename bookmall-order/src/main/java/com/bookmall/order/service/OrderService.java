package com.bookmall.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.order.client.CatalogClient;
import com.bookmall.order.client.NotificationPublisher;
import com.bookmall.order.entity.CartItem;
import com.bookmall.order.entity.OrderItem;
import com.bookmall.order.entity.OrderMain;
import com.bookmall.order.entity.StockHold;
import com.bookmall.order.mapper.CartItemMapper;
import com.bookmall.order.mapper.OrderItemMapper;
import com.bookmall.order.mapper.OrderMainMapper;
import com.bookmall.order.mapper.StockHoldMapper;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.PageQuery;
import com.bookmall.common.web.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 下单、订单列表和订单详情。
 * <p>
 * 第一版只结算当前用户的全部购物车，不支持勾选部分商品。同一用户的下单会串行执行。
 * 扣库存前先提交占用令牌；写订单失败或进程中断时，按同一令牌把库存加回。
 * 订单落库成功后再投递 MQ 通知，投递失败不影响订单。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 订单号后缀使用的字符，避开容易混淆的小写字母。 */
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /** 订单号前缀：UTC 的年月日时分秒。 */
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 购物车。 */
    private final CartItemMapper carts;

    /** 订单主表。 */
    private final OrderMainMapper orders;

    /** 订单明细。 */
    private final OrderItemMapper items;

    /** 目录服务，用于查书和扣库存。 */
    private final CatalogClient catalog;

    /** 经 RabbitMQ 投递站内信。 */
    private final NotificationPublisher notifications;

    /** 在同一事务里写订单并清空购物车。 */
    private final OrderWriter writer;

    /** 同一用户同时只有一个下单事务。 */
    private final CheckoutLock checkoutLock;

    /** 未完成的库存占用。 */
    private final StockHoldMapper holds;

    /** 把占用单独提交，避免和下单事务一起回滚。 */
    private final StockHoldWriter holdWriter;

    /** 生成订单号后缀。声明时已赋值，不进入构造器。 */
    private final SecureRandom random = new SecureRandom();

    /**
     * 用当前购物车创建一笔订单。
     * <p>
     * 请求体里如果带了 {@code itemIds}，直接拒绝，避免调用方以为可以部分结算。
     * 校验顺序是：购物车非空、图书都存在、都在售、库存都够，然后才扣库存和写订单。
     *
     * @param userId 当前用户
     * @param body   可空；出现 itemIds 时视为参数不合法
     * @return 新订单详情
     */
    @Transactional
    public Map<String, Object> create(long userId, JsonNode body) {
        if (body != null && body.has("itemIds")) {
            throw BusinessException.badRequest("第一版仅支持结算全部购物车");
        }
        checkoutLock.lock(userId);
        if (TransactionAspectSupport.currentTransactionStatus().isNewTransaction()) {
            recoverHolds(userId);
        }
        List<CartItem> cart = carts.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
        if (cart.isEmpty()) {
            throw new BusinessException(400, 40005, "购物车为空");
        }
        List<Line> lines = cart.stream()
                .map(item -> new Line(item, catalog.getBook(item.getBookId())))
                .toList();
        List<Map<String, Object>> missing = lines.stream()
                .filter(line -> !line.book().exists())
                .map(line -> Map.<String, Object>of("bookId", line.book().bookId()))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(404, 40400, "图书不存在", Map.of("items", missing));
        }
        List<Map<String, Object>> offSale = lines.stream()
                .filter(line -> !"ON_SALE".equals(line.book().status()))
                .map(line -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookId", line.book().bookId());
                    row.put("bookTitle", line.book().title());
                    row.put("status", line.book().status());
                    return row;
                }).toList();
        if (!offSale.isEmpty()) {
            throw new BusinessException(400, 40004, "图书未上架", Map.of("items", offSale));
        }
        List<Map<String, Object>> shortStock = lines.stream()
                .filter(line -> line.item().getQuantity() > line.book().stock())
                .map(line -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookId", line.book().bookId());
                    row.put("bookTitle", line.book().title());
                    row.put("stock", line.book().stock());
                    row.put("quantity", line.item().getQuantity());
                    return row;
                }).toList();
        if (!shortStock.isEmpty()) {
            throw new BusinessException(400, 40006, "库存不足", Map.of("items", shortStock));
        }

        List<Hold> pending = new ArrayList<>();
        for (Line line : lines) {
            pending.add(new Hold(line.book().bookId(), line.item().getQuantity(), UUID.randomUUID().toString()));
        }
        persistHolds(userId, pending);
        List<Hold> attempted = new ArrayList<>();
        OrderMain order;
        try {
            for (Hold hold : pending) {
                attempted.add(hold);
                catalog.deduct(hold.bookId(), hold.quantity(), hold.token());
            }
            BigDecimal total = lines.stream()
                    .map(line -> Texts.times(line.book().price(), line.item().getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order = writer.save(userId, nextOrderNo(), total, lines);
            holds.delete(Wrappers.<StockHold>lambdaQuery().eq(StockHold::getUserId, userId));
        } catch (RuntimeException ex) {
            compensate(userId, attempted, ex);
            throw ex;
        }
        notifications.orderCreated(userId, order.getOrderNo(), Texts.money(order.getTotalAmount()));
        return detailView(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId)));
    }

    /**
     * 取消本人的已创建订单：先改状态再还库存，最后投递取消通知。
     * <p>
     * 故意不加类级大事务包住 HTTP 还库存：状态更新各自提交，还库存失败时把状态改回 CREATED，
     * 避免「库已 CANCELLED 但库存未还」或事务回滚导致库存已还、订单仍 CREATED。
     * 他人订单或不存在统一 404，避免探测。
     */
    public Map<String, Object> cancel(long userId, long orderId) {
        OrderMain order = orders.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId));
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderMain.CREATED) {
            throw new BusinessException(400, 40008, "订单状态不允许取消");
        }
        // 先占住 CANCELLED，避免并发双重还库存；还库存失败则改回 CREATED。
        int updated = orders.update(null, Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, OrderMain.CANCELLED)
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId)
                .eq(OrderMain::getStatus, OrderMain.CREATED));
        if (updated != 1) {
            throw new BusinessException(400, 40008, "订单状态不允许取消");
        }
        List<OrderItem> orderItems = items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        try {
            for (OrderItem item : orderItems) {
                catalog.restoreQuantity(item.getBookId(), item.getQuantity());
            }
        } catch (RuntimeException ex) {
            orders.update(null, Wrappers.<OrderMain>lambdaUpdate()
                    .set(OrderMain::getStatus, OrderMain.CREATED)
                    .eq(OrderMain::getId, orderId)
                    .eq(OrderMain::getStatus, OrderMain.CANCELLED));
            log.error("取消订单还库存失败，已回滚状态 orderId={}", orderId, ex);
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        order.setStatus(OrderMain.CANCELLED);
        notifications.orderCancelled(userId, order.getOrderNo());
        return detailView(order, orderItems);
    }

    /**
     * 把上次中断留下的占用按原令牌加回库存，并提交删除。
     * 回补没有落地时保留占用，下次下单继续处理。
     */
    private void recoverHolds(long userId) {
        List<StockHold> leftover = holds.selectList(Wrappers.<StockHold>lambdaQuery()
                .eq(StockHold::getUserId, userId)
                .orderByAsc(StockHold::getToken));
        for (StockHold row : leftover) {
            catalog.restore(row.getBookId(), row.getQuantity(), row.getToken());
        }
        if (!leftover.isEmpty()) {
            holdWriter.deleteByUser(userId);
        }
    }

    /** 下单事务自己提交时，占用单独提交，以便进程中断后仍能找到令牌。 */
    private void persistHolds(long userId, List<Hold> pending) {
        List<StockHold> rows = pending.stream().map(hold -> {
            StockHold row = new StockHold();
            row.setToken(hold.token());
            row.setUserId(userId);
            row.setBookId(hold.bookId());
            row.setQuantity(hold.quantity());
            return row;
        }).toList();
        if (TransactionAspectSupport.currentTransactionStatus().isNewTransaction()) {
            holdWriter.commit(rows);
        } else {
            rows.forEach(holds::insert);
        }
    }

    /**
     * 按令牌回补已尝试扣减的库存。全部回补成功时保留原始业务错误；任一回补失败则返回服务器内部错误。
     */
    private void compensate(long userId, List<Hold> attempted, RuntimeException cause) {
        boolean failed = false;
        for (int i = attempted.size() - 1; i >= 0; i--) {
            Hold hold = attempted.get(i);
            try {
                catalog.restore(hold.bookId(), hold.quantity(), hold.token());
                log.error("扣减后下单失败，库存已回补 userId={} bookId={} quantity={} token={}",
                        userId, hold.bookId(), hold.quantity(), hold.token(), cause);
            } catch (RuntimeException ex) {
                failed = true;
                log.error("回补库存失败 userId={} bookId={} quantity={} token={}",
                        userId, hold.bookId(), hold.quantity(), hold.token(), ex);
            }
        }
        if (failed) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
    }

    /**
     * 分页查询当前用户的订单，按创建时间倒序。itemCount 是购买册数合计，不是明细行数。
     *
     * @param userId 当前用户
     * @param page   页码，缺省 1
     * @param size   每页条数，缺省 10
     * @return 订单摘要分页
     */
    public PageResult<Map<String, Object>> list(long userId, Integer page, Integer size) {
        PageQuery query = PageQuery.of(page, size);
        Page<OrderMain> result = orders.selectPage(new Page<>(query.page(), query.size()),
                Wrappers.<OrderMain>lambdaQuery()
                        .eq(OrderMain::getUserId, userId)
                        .orderByDesc(OrderMain::getCreatedAt));
        List<Long> ids = result.getRecords().stream().map(OrderMain::getId).toList();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (OrderItem item : items.selectList(Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderId, ids))) {
                counts.merge(item.getOrderId(), item.getQuantity(), Integer::sum);
            }
        }
        List<Map<String, Object>> rows = result.getRecords().stream().map(order -> {
            Map<String, Object> row = header(order);
            row.put("itemCount", counts.getOrDefault(order.getId(), 0));
            return row;
        }).toList();
        return new PageResult<>(query.page(), query.size(), result.getTotal(), rows);
    }

    /**
     * 查询一笔属于当前用户的订单。其他用户的订单与不存在同样返回 404。
     *
     * @param userId  当前用户
     * @param orderId 订单主键
     * @return 订单头和明细
     */
    public Map<String, Object> detail(long userId, long orderId) {
        OrderMain order = orders.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId));
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        return detailView(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId)));
    }

    /**
     * 生成订单号：14 位 UTC 时间加 6 位随机字符。最多试 5 次，仍撞号则按内部错误返回。
     */
    private String nextOrderNo() {
        String prefix = OffsetDateTime.now(ZoneOffset.UTC).format(ORDER_TIME);
        for (int i = 0; i < 5; i++) {
            StringBuilder suffix = new StringBuilder();
            for (int n = 0; n < 6; n++) {
                suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            String orderNo = prefix + suffix;
            if (orders.selectCount(Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getOrderNo, orderNo)) == 0) {
                return orderNo;
            }
        }
        throw new BusinessException(500, 50000, "服务器内部错误");
    }

    /** 详情视图：订单头、购买册数合计、每行小计。 */
    private Map<String, Object> detailView(OrderMain order, List<OrderItem> orderItems) {
        Map<String, Object> row = header(order);
        int count = 0;
        List<Map<String, Object>> views = new ArrayList<>();
        for (OrderItem item : orderItems) {
            count += item.getQuantity();
            BigDecimal subtotal = Texts.times(item.getUnitPrice(), item.getQuantity());
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", item.getId());
            line.put("bookId", item.getBookId());
            line.put("bookTitle", item.getBookTitle());
            line.put("unitPrice", Texts.money(item.getUnitPrice()));
            line.put("quantity", item.getQuantity());
            line.put("subtotal", Texts.money(subtotal));
            views.add(line);
        }
        row.put("itemCount", count);
        row.put("items", views);
        return row;
    }

    /** 列表和详情共用的订单头。数字状态映射为 CREATED / CANCELLED。 */
    private Map<String, Object> header(OrderMain order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("totalAmount", Texts.money(order.getTotalAmount()));
        short status = order.getStatus() == null ? OrderMain.CREATED : order.getStatus();
        row.put("status", status == OrderMain.CANCELLED ? "CANCELLED" : "CREATED");
        row.put("createdAt", Texts.time(order.getCreatedAt()));
        return row;
    }

    /**
     * 购物车一行和它对应的图书快照。
     *
     * @param item 购物车条目
     * @param book 下单前查到的图书
     */
    public record Line(CartItem item, CatalogClient.BookSnapshot book) {
    }

    /**
     * 本轮准备扣减的一本书。
     *
     * @param bookId   图书主键
     * @param quantity 数量
     * @param token    扣减令牌
     */
    private record Hold(long bookId, int quantity, String token) {
    }
}
