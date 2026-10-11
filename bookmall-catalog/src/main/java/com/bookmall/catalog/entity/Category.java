package com.bookmall.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 图书分类，对应表 {@code category}。分类由种子数据维护，接口只读。
 */
@Getter
@Setter
@TableName("category")
public class Category {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称。 */
    private String name;
}
