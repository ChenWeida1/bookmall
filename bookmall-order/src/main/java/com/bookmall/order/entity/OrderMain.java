package com.bookmall.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 订单主表，对应表 {@code order_main}。
 * <p>
 * 第一版只有“已创建”一种状态，用 {@link #CREATED} 表示。订单号在服务层生成。
 * {@code createdAt} 在插入时由 {@link com.bookmall.order.config.CreatedAtFillHandler} 写成 UTC。
 */
@Getter
@Setter
@TableName("order_main")
public class OrderMain {

    /** 已创建。下单成功后即为此状态。 */
    public static final short CREATED = 1;

    /** 已取消。仅可由 CREATED 转入。 */
    public static final short CANCELLED = 2;

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外展示的订单号，格式为 UTC 时间加 6 位随机字符。 */
    private String orderNo;

    /** 下单用户。 */
    private Long userId;

    /** 订单合计，单位元。 */
    private BigDecimal totalAmount;

    /** 订单状态：{@link #CREATED} 或 {@link #CANCELLED}。 */
    private Short status;

    /** 下单时间，UTC。插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
