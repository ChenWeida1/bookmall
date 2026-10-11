package com.bookmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.order.entity.StockHold;
import org.apache.ibatis.annotations.Mapper;

/**
 * 未完成下单的库存占用。
 */
@Mapper
public interface StockHoldMapper extends BaseMapper<StockHold> {
}
