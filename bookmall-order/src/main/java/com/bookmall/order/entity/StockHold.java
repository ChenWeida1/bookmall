package com.bookmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 尚未和订单一起提交的库存占用，对应表 {@code stock_hold}。
 * <p>
 * 扣库存之前先把令牌提交到订单库。进程在扣减和写订单之间中断时，下次下单按这些令牌回补。
 */
@Getter
@Setter
@TableName("stock_hold")
public class StockHold {

    /** 与图书库 {@code stock_change.token} 相同的令牌。 */
    @TableId(value = "token", type = IdType.INPUT)
    private String token;

    /** 下单用户。 */
    private Long userId;

    /** 图书主键。 */
    private Long bookId;

    /** 占用数量。 */
    private Integer quantity;
}
