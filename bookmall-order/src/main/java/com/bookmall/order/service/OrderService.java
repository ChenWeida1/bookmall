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
import com.bookmall.order.mq.PayTimeoutPublisher;
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
 * 可以结算全部购物车，也可以只结算请求体里的 {@code itemIds}。同一用户的下单会串行执行。
 * 扣库存前先提交占用令牌；写订单失败或进程中断时，按同一令牌把库存加回。
 * 新订单是待支付。落库成功后投递下单通知和支付超时消息，投递失败不影响订单。
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

    /** 待支付超时后自动取消。 */
    private final PayTimeoutPublisher payTimeouts;

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
     * 用购物车创建一笔待支付订单。
     * <p>
     * 请求体省略 {@code itemIds} 时结算全部购物车。空数组拒绝。
     * 校验顺序是：条目合法、购物车非空、图书都存在、都在售、库存都够，然后才扣库存和写订单。
     *
     * @param userId 当前用户
     * @param body   可空；可含 itemIds
     * @return 新订单详情
     */
    @Transactional
    public Map<String, Object> create(long userId, JsonNode body) {
        List<Long> itemIds = readItemIds(body);
        // 同一用户串行下单，避免连点读到同一份购物车、扣两次库存。
        checkoutLock.lock(userId);
        if (TransactionAspectSupport.currentTransactionStatus().isNewTransaction()) {
            // 上次进程在扣库存和写订单之间停掉时，stock_hold 里还留着令牌。先按原令牌把库存加回。
            recoverHolds(userId);
        }
        List<Line> lines = loadCheckoutLines(userId, itemIds);

        // 每一本书一个新令牌。图书服务用它保证同一笔只扣一次、只补一次。
        List<Hold> pending = new ArrayList<>();
        for (Line line : lines) {
            pending.add(new Hold(line.book().bookId(), line.item().getQuantity(), UUID.randomUUID().toString()));
        }
        // 先单独提交占用。后面扣库存的 HTTP 成功后进程若退出，订单库仍能找到这些令牌。
        persistHolds(userId, pending);
        List<Hold> attempted = new ArrayList<>();
        OrderMain order;
        try {
            for (int i = 0; i < pending.size(); i++) {
                Hold hold = pending.get(i);
                attempted.add(hold);
                try {
                    catalog.deduct(hold.bookId(), hold.quantity(), hold.token());
                } catch (BusinessException ex) {
                    // 校验之后库存仍可能被别人买走。对外仍按整单库存不足返回，并带上 data.items。
                    if (ex.getCode() != 40006) {
                        throw ex;
                    }
                    throw shortage(lines.get(i), ex.getData());
                }
            }
            BigDecimal total = lines.stream()
                    .map(line -> Texts.times(line.book().price(), line.item().getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order = writer.save(userId, nextOrderNo(), total, lines);
            // 订单已写下，占用完成，删掉令牌。库存保持已扣状态。
            holds.delete(Wrappers.<StockHold>lambdaQuery().eq(StockHold::getUserId, userId));
        } catch (RuntimeException ex) {
            // 写订单失败：用同一令牌回补。令牌已回补过的不会再次加库存。
            compensate(userId, attempted, ex);
            throw ex;
        }
        // 通知和超时消息失败只记日志，不回滚已经成功的订单。
        notifications.orderCreated(userId, order.getOrderNo(), Texts.money(order.getTotalAmount()));
        payTimeouts.schedule(order.getId(), userId);
        return detailView(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId)));
    }

    /**
     * 取消本人的待支付订单：先改状态再还库存，最后投递取消通知。
     * <p>
     * 故意不加类级大事务包住 HTTP 还库存：先把状态改成 CANCELLED，再按明细加库存。
     * 中途失败时，已加回的数量用扣减接口扣回去，扣回成功才改回待支付。
     * 扣不回去就保持 CANCELLED，避免再次取消把同一批库存再加一次。
     * 他人订单或不存在统一 404，避免探测。已支付不可取消。
     */
    public Map<String, Object> cancel(long userId, long orderId) {
        OrderMain order = orders.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId));
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderMain.PENDING_PAY) {
            throw new BusinessException(400, 40008, "订单状态不允许取消");
        }
        // 先占住 CANCELLED，避免两个取消请求一起还库存。
        int updated = orders.update(null, Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, OrderMain.CANCELLED)
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId)
                .eq(OrderMain::getStatus, OrderMain.PENDING_PAY));
        if (updated != 1) {
            throw new BusinessException(400, 40008, "订单状态不允许取消");
        }
        List<OrderItem> orderItems = items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        List<OrderItem> restored = new ArrayList<>();
        try {
            for (OrderItem item : orderItems) {
                catalog.restoreQuantity(item.getBookId(), item.getQuantity());
                restored.add(item);
            }
        } catch (RuntimeException ex) {
            // 前面几本已经加回。先按同样数量扣回，扣回成功才改回待支付。
            // 扣不回去就保持 CANCELLED，避免用户再次取消把库存再加一遍。
            log.error("取消订单还库存失败 orderId={} userId={}", orderId, userId, ex);
            if (undoCancelRestores(orderId, restored)) {
                orders.update(null, Wrappers.<OrderMain>lambdaUpdate()
                        .set(OrderMain::getStatus, OrderMain.PENDING_PAY)
                        .eq(OrderMain::getId, orderId)
                        .eq(OrderMain::getUserId, userId)
                        .eq(OrderMain::getStatus, OrderMain.CANCELLED));
            } else {
                log.error("取消订单已还库存未能扣回，保持取消状态以免重复加库存 orderId={} userId={}", orderId, userId);
            }
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        order.setStatus(OrderMain.CANCELLED);
        notifications.orderCancelled(userId, order.getOrderNo());
        return detailView(order, orderItems);
    }

    /**
     * 模拟支付：待支付改为已支付，不再扣减或加回库存。
     * 已支付、已取消都返回 40008。重复点击不会第二次改状态。
     */
    public Map<String, Object> pay(long userId, long orderId) {
        OrderMain order = orders.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId));
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        int updated = orders.update(null, Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, OrderMain.PAID)
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId)
                .eq(OrderMain::getStatus, OrderMain.PENDING_PAY));
        if (updated != 1) {
            throw new BusinessException(400, 40008, "订单状态不允许支付");
        }
        order.setStatus(OrderMain.PAID);
        notifications.orderPaid(userId, order.getOrderNo(), Texts.money(order.getTotalAmount()));
        return detailView(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId)));
    }

    /**
     * 超时关单。已经支付或取消时直接返回，供消息消费端确认，避免重复还库存。
     */
    public void expireIfPending(long userId, long orderId) {
        OrderMain order = orders.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getId, orderId)
                .eq(OrderMain::getUserId, userId));
        if (order == null || order.getStatus() == null || order.getStatus() != OrderMain.PENDING_PAY) {
            return;
        }
        cancel(userId, orderId);
    }

    /**
     * 解析勾选的购物车主键。缺省或 JSON null 表示结算全部。
     * 空数组、非数组、非正整数都是参数不合法。
     */
    private static List<Long> readItemIds(JsonNode body) {
        if (body == null || body.isNull() || !body.has("itemIds") || body.get("itemIds").isNull()) {
            return null;
        }
        JsonNode node = body.get("itemIds");
        if (!node.isArray()) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (node.isEmpty()) {
            throw BusinessException.badRequest("请选择要结算的商品");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || item.asLong() <= 0) {
                throw BusinessException.badRequest("参数不合法");
            }
            long id = item.asLong();
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 把这次取消已经加回的库存再扣回去。
     *
     * @return 每一本都扣回去了
     */
    private boolean undoCancelRestores(long orderId, List<OrderItem> restored) {
        boolean ok = true;
        for (int i = restored.size() - 1; i >= 0; i--) {
            OrderItem item = restored.get(i);
            // 这是回滚用的新令牌，不是下单时的占用令牌。长度必须落在扣减接口的 1–64。
            String token = "cancel-undo-" + UUID.randomUUID().toString().replace("-", "");
            try {
                catalog.deduct(item.getBookId(), item.getQuantity(), token);
            } catch (RuntimeException ex) {
                ok = false;
                log.error("取消订单回滚库存失败 orderId={} bookId={} quantity={}",
                        orderId, item.getBookId(), item.getQuantity(), ex);
            }
        }
        return ok;
    }

    /** 扣减阶段发现库存不足时，仍按创建订单接口的 data.items 结构返回。 */
    private static BusinessException shortage(Line line, Object data) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bookId", line.book().bookId());
        row.put("bookTitle", line.book().title());
        row.put("stock", stockOf(data, line.book().stock()));
        row.put("quantity", line.item().getQuantity());
        return new BusinessException(400, 40006, "库存不足", Map.of("items", List.of(row)));
    }

    /** 目录服务若带回了扣减时的库存，用那个数；否则退回下单前看到的库存。 */
    private static int stockOf(Object data, int fallback) {
        if (data instanceof JsonNode node && node.path("stock").canConvertToInt()) {
            return node.path("stock").asInt();
        }
        if (data instanceof Map<?, ?> map && map.get("stock") instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    /**
     * 读取要结算的购物车行，并按固定顺序核对每一行。
     * {@code itemIds} 为空时取全部。指定了主键却对不上本人购物车时，按参数不合法返回。
     * 购物车为空、图书不存在、未上架、库存不足都在这里抛出，调用方拿到的就是可以扣库存的明细。
     */
    private List<Line> loadCheckoutLines(long userId, List<Long> itemIds) {
        var query = Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId);
        if (itemIds != null) {
            query.in(CartItem::getId, itemIds);
        }
        List<CartItem> cart = carts.selectList(query);
        if (itemIds != null && cart.size() != itemIds.size()) {
            throw BusinessException.badRequest("购物车条目不存在");
        }
        if (cart.isEmpty()) {
            throw new BusinessException(400, 40005, "购物车为空");
        }
        // 购物车只存 bookId，书名、价格、库存以图书服务此刻的快照为准。
        List<Line> lines = cart.stream()
                .map(item -> new Line(item, catalog.getBook(item.getBookId())))
                .toList();
        // 缺书、下架、库存不足都整单失败，并带上是哪些书。
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
        return lines;
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
     * @param status 可选，PENDING_PAY / PAID / CANCELLED；空表示全部
     * @return 订单摘要分页
     */
    public PageResult<Map<String, Object>> list(long userId, Integer page, Integer size, String status) {
        PageQuery query = PageQuery.of(page, size);
        Short statusCode = parseStatusFilter(status);
        var wrapper = Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getUserId, userId);
        if (statusCode != null) {
            wrapper.eq(OrderMain::getStatus, statusCode);
        }
        wrapper.orderByDesc(OrderMain::getCreatedAt);
        Page<OrderMain> result = orders.selectPage(new Page<>(query.page(), query.size()), wrapper);
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
     * 管理员分页查看全部用户的订单。摘要比读者列表多一个 userId。
     */
    public PageResult<Map<String, Object>> adminList(Integer page, Integer size, String status) {
        PageQuery query = PageQuery.of(page, size);
        Short statusCode = parseStatusFilter(status);
        var wrapper = Wrappers.<OrderMain>lambdaQuery();
        if (statusCode != null) {
            wrapper.eq(OrderMain::getStatus, statusCode);
        }
        wrapper.orderByDesc(OrderMain::getCreatedAt);
        Page<OrderMain> result = orders.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<Map<String, Object>> rows = result.getRecords().stream().map(order -> {
            Map<String, Object> row = header(order);
            row.put("userId", order.getUserId());
            return row;
        }).toList();
        return new PageResult<>(query.page(), query.size(), result.getTotal(), rows);
    }

    /**
     * 管理员查看任意用户的订单详情。不存在返回 404。
     */
    public Map<String, Object> adminDetail(long orderId) {
        OrderMain order = orders.selectById(orderId);
        if (order == null) {
            throw BusinessException.notFound("订单不存在");
        }
        Map<String, Object> view = detailView(order, items.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId)));
        view.put("userId", order.getUserId());
        return view;
    }

    /** 空状态表示不筛选。非法取值返回 40001。 */
    private static Short parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim()) {
            case "PENDING_PAY" -> OrderMain.PENDING_PAY;
            case "CANCELLED" -> OrderMain.CANCELLED;
            case "PAID" -> OrderMain.PAID;
            default -> throw BusinessException.badRequest("参数不合法");
        };
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

    /** 列表和详情共用的订单头。数字状态映射为 PENDING_PAY / CANCELLED / PAID。 */
    private Map<String, Object> header(OrderMain order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("totalAmount", Texts.money(order.getTotalAmount()));
        row.put("status", OrderMain.statusName(order.getStatus()));
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
