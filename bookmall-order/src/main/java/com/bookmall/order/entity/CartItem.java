package com.bookmall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 购物车条目，对应表 {@code cart_item}。
 * <p>
 * 同一用户对同一本书只保留一行，再次加入时累加数量。价格和书名不落在购物车里，展示时向目录服务实时查询。
 */
@Getter
@Setter
@TableName("cart_item")
public class CartItem {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户。用户服务在另一个库，这里只存编号。 */
    private Long userId;

    /** 图书主键，对应目录服务中的书。 */
    private Long bookId;

    /** 购买数量，至少为 1；改为 0 时删除该行。 */
    private Integer quantity;
}
