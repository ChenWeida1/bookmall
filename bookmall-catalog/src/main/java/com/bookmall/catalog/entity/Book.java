package com.bookmall.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 图书，对应表 {@code book}。
 * <p>
 * 上架状态用数字保存：{@link #ON_SALE} 为在售，{@link #OFF_SALE} 为下架。
 * 列表只返回在售图书；详情仍可查到下架书，并在 {@code status} 中标明。
 * {@code categoryName} 不在本表，由查询时关联 {@code category} 填入。
 */
@Getter
@Setter
@TableName("book")
public class Book {

    /** 在售。 */
    public static final short ON_SALE = 1;

    /** 下架。 */
    public static final short OFF_SALE = 0;

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属分类主键。 */
    private Long categoryId;

    /** 分类名称，仅查询结果使用，不写入 {@code book} 表。 */
    @TableField(exist = false)
    private String categoryName;

    /** 书名。 */
    private String title;

    /** 作者。 */
    private String author;

    /** 封面地址，可空。 */
    private String coverUrl;

    /** 单价，单位元，两位小数。 */
    private BigDecimal price;

    /** 可售库存。扣减时要求库存不少于购买数量。 */
    private Integer stock;

    /** 图书简介，仅详情接口返回。 */
    private String description;

    /** 上架状态，取值为 {@link #ON_SALE} 或 {@link #OFF_SALE}。 */
    private Short status;

    /**
     * 把库中的状态数字转成接口使用的枚举名。
     *
     * @param status {@link #ON_SALE} 或 {@link #OFF_SALE}
     * @return {@code ON_SALE} 或 {@code OFF_SALE}
     */
    public static String statusName(Short status) {
        return status != null && status == ON_SALE ? "ON_SALE" : "OFF_SALE";
    }
}
