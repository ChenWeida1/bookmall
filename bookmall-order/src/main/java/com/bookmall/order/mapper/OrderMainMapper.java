package com.bookmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.order.entity.OrderMain;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单主表访问。列表和详情都限定当前用户，不能按订单号跨用户查询。
 */
@Mapper
public interface OrderMainMapper extends BaseMapper<OrderMain> {
}
