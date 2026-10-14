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
 * 第三版状态：{@link #PENDING_PAY} 待支付、{@link #CANCELLED} 已取消、{@link #PAID} 已支付。
 * 下单即扣库存并进入待支付；模拟支付只改状态，不再动库存。订单号在服务层生成。
 * {@code createdAt} 在插入时由 {@link com.bookmall.order.config.CreatedAtFillHandler} 写成 UTC。
 */
@Getter
@Setter
@TableName("order_main")
public class OrderMain {

    /** 待支付。下单成功后即为此状态，库存已经扣减。 */
    public static final short PENDING_PAY = 1;

    /** 已取消。仅可由待支付转入，库存按明细加回。 */
    public static final short CANCELLED = 2;

    /** 已支付。仅可由待支付转入，不再扣减或加回库存。 */
    public static final short PAID = 3;

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外展示的订单号，格式为 UTC 时间加 6 位随机字符。 */
    private String orderNo;

    /** 下单用户。 */
    private Long userId;

    /** 订单合计，单位元。 */
    private BigDecimal totalAmount;

    /** 订单状态：{@link #PENDING_PAY}、{@link #CANCELLED} 或 {@link #PAID}。 */
    private Short status;

    /**
     * 库内数字转接口字符串。空值按待支付，避免新订单在填充前被当成已支付。
     *
     * @param status 库内状态
     * @return {@code PENDING_PAY}、{@code CANCELLED} 或 {@code PAID}
     */
    public static String statusName(Short status) {
        if (status != null && status == CANCELLED) {
            return "CANCELLED";
        }
        if (status != null && status == PAID) {
            return "PAID";
        }
        return "PENDING_PAY";
    }

    /** 下单时间，UTC。插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
