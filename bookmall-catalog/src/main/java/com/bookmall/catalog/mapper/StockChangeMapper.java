package com.bookmall.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.catalog.entity.StockChange;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存变更令牌。插入使用冲突忽略，避免同一 token 的重试再扣一次。
 */
@Mapper
public interface StockChangeMapper extends BaseMapper<StockChange> {

    /**
     * 登记一次新的扣减。令牌已存在时不插入、不报错。
     *
     * @param change 令牌、图书和数量
     * @return 1 表示首次登记，0 表示该令牌已经登记过
     */
    @Insert("""
            INSERT INTO stock_change (token, book_id, quantity, kind)
            VALUES (#{token}, #{bookId}, #{quantity}, 1)
            ON CONFLICT (token) DO NOTHING
            """)
    int insertDeduct(StockChange change);

    /**
     * 锁住这条令牌，直到当前事务结束。
     *
     * @param token 扣减令牌
     * @return 记录；令牌尚未登记时为 null
     */
    @Select("""
            SELECT token, book_id, quantity, kind
            FROM stock_change
            WHERE token = #{token}
            FOR UPDATE
            """)
    StockChange lock(String token);

    /**
     * 把已扣减的令牌标成已回补。只有当前仍是已扣减时才更新。
     *
     * @param token 扣减令牌
     * @return 实际更新行数
     */
    @Update("UPDATE stock_change SET kind = 2 WHERE token = #{token} AND kind = 1")
    int markRestored(String token);
}
