package com.bookmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 订单明细，对应表 {@code order_item}。
 * <p>
 * 书名和单价在下单时从目录服务复制过来。之后图书改价或下架，已生成的订单仍按当时的快照展示。
 */
@Getter
@Setter
@TableName("order_item")
public class OrderItem {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属订单主键。 */
    private Long orderId;

    /** 下单时的图书主键，便于回查，不作为外键跨库约束。 */
    private Long bookId;

    /** 下单时的书名快照。 */
    private String bookTitle;

    /** 下单时的单价快照，单位元。 */
    private BigDecimal unitPrice;

    /** 购买数量。 */
    private Integer quantity;
}
