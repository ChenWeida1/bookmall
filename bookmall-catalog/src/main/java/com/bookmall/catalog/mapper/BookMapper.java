package com.bookmall.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.catalog.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 图书查询与库存变更。
 * <p>
 * 列表只查在售图书。关键词按书名模糊匹配，并转义 {@code %}、{@code _} 和反斜杠，避免用户输入被当成通配符。
 * 扣减用一条带库存条件的更新，更新行数为 0 表示库存不足或图书不存在。
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {

    /**
     * 按分类和书名搜索在售图书，按主键升序分页。分类名称一并查出。
     *
     * @param page       页码从 1 开始
     * @param categoryId 分类主键，null 表示不限分类
     * @param keyword    已转义的 LIKE 模式，null 表示不按书名过滤
     * @return 当前页图书及总数
     */
    @Select("""
            SELECT b.id, b.category_id, b.title, b.author, b.cover_url, b.price,
                   b.stock, b.description, b.on_sale, c.name AS category_name
            FROM book b
            INNER JOIN category c ON c.id = b.category_id
            WHERE b.on_sale = true
              AND (CAST(#{categoryId,jdbcType=BIGINT} AS BIGINT) IS NULL OR b.category_id = #{categoryId,jdbcType=BIGINT})
              AND (CAST(#{keyword,jdbcType=VARCHAR} AS VARCHAR) IS NULL OR lower(b.title) LIKE #{keyword,jdbcType=VARCHAR} ESCAPE '\\')
            ORDER BY b.id ASC
            """)
    Page<Book> search(Page<Book> page, @Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /**
     * 给客服查书。包含下架书，最多 8 本。三个条件都是已转义的 LIKE 模式，null 表示不限制。
     */
    @Select("""
            SELECT b.id, b.category_id, b.title, b.author, b.cover_url, b.price,
                   b.stock, b.description, b.on_sale, c.name AS category_name
            FROM book b
            INNER JOIN category c ON c.id = b.category_id
            WHERE (CAST(#{title,jdbcType=VARCHAR} AS VARCHAR) IS NULL OR lower(b.title) LIKE #{title,jdbcType=VARCHAR} ESCAPE '\\')
              AND (CAST(#{author,jdbcType=VARCHAR} AS VARCHAR) IS NULL OR lower(b.author) LIKE #{author,jdbcType=VARCHAR} ESCAPE '\\')
              AND (CAST(#{category,jdbcType=VARCHAR} AS VARCHAR) IS NULL OR lower(c.name) LIKE #{category,jdbcType=VARCHAR} ESCAPE '\\')
            ORDER BY b.id ASC
            LIMIT 8
            """)
    List<Book> searchForAssistant(@Param("title") String title, @Param("author") String author, @Param("category") String category);

    /**
     * 在库存足够时扣减。条件写在 WHERE 中，避免先读后写造成超卖。
     *
     * @param id       图书主键
     * @param quantity 扣减数量，必须为正
     * @return 实际更新行数，0 表示未扣减
     */
    @Update("UPDATE book SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int deduct(@Param("id") long id, @Param("quantity") int quantity);

    /**
     * 下单失败后把已扣减的库存加回去。
     *
     * @param id       图书主键
     * @param quantity 回补数量
     * @return 实际更新行数
     */
    @Update("UPDATE book SET stock = stock + #{quantity} WHERE id = #{id}")
    int restore(@Param("id") long id, @Param("quantity") int quantity);

    /**
     * 管理端改上下架状态。
     *
     * @param id     图书主键
     * @param onSale true 在售 / false 下架
     * @return 更新行数
     */
    @Update("UPDATE book SET on_sale = #{onSale} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("onSale") boolean onSale);

    /**
     * 管理端直接改库存（覆盖写）。
     */
    @Update("UPDATE book SET stock = #{stock} WHERE id = #{id} AND #{stock} >= 0")
    int updateStock(@Param("id") long id, @Param("stock") int stock);
}
