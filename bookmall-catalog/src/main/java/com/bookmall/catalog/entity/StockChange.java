package com.bookmall.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 一次库存扣减的幂等记录，对应表 {@code stock_change}。
 * <p>
 * {@code deducted} 为 true 表示该 token 已经扣过库存，false 表示这笔扣减已经加回。
 * 同一 token 再扣或再补都不再改库存。
 */
@Getter
@Setter
@TableName("stock_change")
public class StockChange {

    /** 订单服务生成的扣减令牌。 */
    @TableId(value = "token", type = IdType.INPUT)
    private String token;

    /** 图书主键。 */
    private Long bookId;

    /** 变更数量。 */
    private Integer quantity;

    /** true 已扣减，false 已回补。 */
    private Boolean deducted;
}
