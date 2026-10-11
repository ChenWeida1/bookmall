package com.bookmall.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.order.entity.StockHold;
import com.bookmall.order.mapper.StockHoldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 把库存占用单独提交，使下单事务回滚或进程退出后这些令牌仍然留在库里。
 */
@Service
@RequiredArgsConstructor
public class StockHoldWriter {

    /** 占用表。 */
    private final StockHoldMapper holds;

    /**
     * 提交这一轮扣减令牌。调用方的下单事务回滚时，这些行仍然保留。
     *
     * @param rows 本轮每一本书的占用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commit(List<StockHold> rows) {
        for (StockHold row : rows) {
            holds.insert(row);
        }
    }

    /**
     * 删掉该用户已经回补完成的占用，并立刻提交。
     *
     * @param userId 下单用户
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByUser(long userId) {
        holds.delete(Wrappers.<StockHold>lambdaQuery().eq(StockHold::getUserId, userId));
    }
}
