package com.bookmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细访问。列表页用一次查询取出多笔订单的明细，避免逐单查询。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
