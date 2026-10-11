package com.bookmall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.order.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 购物车表 {@code cart_item} 的访问。查询都带用户编号，避免一个用户改到另一个用户的条目。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 加入一本或把数量累加到已有行。并发插入同一用户的同一本书时合并为一行。
     *
     * @param userId   当前用户
     * @param bookId   图书主键
     * @param quantity 本次增加的数量
     * @return 合并后的购物车行
     */
    @Select("""
            INSERT INTO cart_item (user_id, book_id, quantity)
            VALUES (#{userId}, #{bookId}, #{quantity})
            ON CONFLICT (user_id, book_id)
            DO UPDATE SET quantity = cart_item.quantity + EXCLUDED.quantity
            RETURNING id, user_id, book_id, quantity
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    CartItem addQuantity(@Param("userId") long userId, @Param("bookId") long bookId, @Param("quantity") int quantity);
}
