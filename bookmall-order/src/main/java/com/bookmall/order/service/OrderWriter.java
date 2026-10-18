package com.bookmall.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.order.entity.CartItem;
import com.bookmall.order.entity.OrderItem;
import com.bookmall.order.entity.OrderMain;
import com.bookmall.order.mapper.CartItemMapper;
import com.bookmall.order.mapper.OrderItemMapper;
import com.bookmall.order.mapper.OrderMainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 在一个本地事务里保存订单、明细并删除已结算的购物车行。
 * <p>
 * 库存扣减发生在目录服务。这里失败时由 {@link OrderService} 按令牌回补。
 * 单独成类是为了让 {@code @Transactional} 通过 Spring 代理生效，避免同类自调用绕过事务。
 */
@Service
@RequiredArgsConstructor
public class OrderWriter {

    /** 订单主表。 */
    private final OrderMainMapper orders;

    /** 订单明细。 */
    private final OrderItemMapper items;

    /** 购物车。 */
    private final CartItemMapper carts;

    /**
     * 写入待支付订单和明细，然后只删除本次结算的购物车条目。
     *
     * @param userId  下单用户
     * @param orderNo 已生成且未占用的订单号
     * @param total   订单合计
     * @param lines   购物车与图书快照
     * @return 已保存的订单，主键已生成
     */
    @Transactional
    public OrderMain save(long userId, String orderNo, BigDecimal total, List<OrderService.Line> lines) {
        OrderMain order = new OrderMain();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(OrderMain.PENDING_PAY);
        orders.insert(order);
        for (OrderService.Line line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setBookId(line.book().bookId());
            item.setBookTitle(line.book().title());
            item.setUnitPrice(line.book().price());
            item.setQuantity(line.item().getQuantity());
            items.insert(item);
        }
        carts.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, lines.stream().map(line -> line.item().getId()).toList()));
        return order;
    }
}
