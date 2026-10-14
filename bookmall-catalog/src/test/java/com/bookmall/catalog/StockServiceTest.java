package com.bookmall.catalog;

import com.bookmall.catalog.mapper.BookMapper;
import com.bookmall.catalog.service.BookCache;
import com.bookmall.catalog.service.CatalogService;
import com.bookmall.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * 库存扣减的集成测试。事务在用例结束时回滚，不改动种子数据。
 */
@SpringBootTest
@Transactional
class StockServiceTest {

    @Autowired
    CatalogService catalog;

    @Autowired
    BookMapper books;

    @SpyBean
    BookCache cache;

    /**
     * 扣 1 本后库存减 1。再请求远超库存的数量应返回 40006，且库存保持扣减后的值。
     */
    @Test
    void deductDecreasesStockAndRejectsShortage() {
        int before = books.selectById(1L).getStock();
        catalog.deduct(1L, 1, "deduct-once");
        assertEquals(before - 1, books.selectById(1L).getStock());
        BusinessException ex = assertThrows(BusinessException.class, () -> catalog.deduct(1L, 1_000_000, "deduct-too-many"));
        assertEquals(40006, ex.getCode());
        assertEquals(before - 1, books.selectById(1L).getStock());
    }

    /** 同一令牌重复扣减不再减库存；回补只加回一次，未知令牌不改库存。 */
    @Test
    void tokenDeductsAndRestoresOnce() {
        int before = books.selectById(1L).getStock();
        catalog.deduct(1L, 2, "hold-1");
        catalog.deduct(1L, 2, "hold-1");
        assertEquals(before - 2, books.selectById(1L).getStock());
        catalog.restore(1L, 2, "hold-1");
        catalog.restore(1L, 2, "hold-1");
        catalog.restore(1L, 2, "never-deducted");
        assertEquals(before, books.selectById(1L).getStock());
    }

    /** 扣库存时先清缓存，并登记在事务提交后再清一次，避免提交前的旧库存被写回缓存。 */
    @Test
    void deductEvictsCacheAgainAfterCommit() {
        catalog.deduct(1L, 1, "evict-after-commit");
        verify(cache, atLeastOnce()).evictBooks();
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertFalse(synchronizations.isEmpty());
        clearInvocations(cache);
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
        verify(cache, atLeastOnce()).evictBooks();
    }

    /** 只给空白上架状态时拒绝，书的状态和库存都不变。 */
    @Test
    void blankStatusDoesNotUpdateBook() {
        var book = books.selectById(1L);
        short status = book.getStatus();
        int stock = book.getStock();
        BusinessException ex = assertThrows(BusinessException.class, () -> catalog.adminUpdate(1L, "  ", null));
        assertEquals(40001, ex.getCode());
        var after = books.selectById(1L);
        assertEquals(status, after.getStatus());
        assertEquals(stock, after.getStock());
    }

    /** 负库存按参数不合法拒绝，不改现有库存。 */
    @Test
    void negativeStockIsRejected() {
        int before = books.selectById(1L).getStock();
        BusinessException ex = assertThrows(BusinessException.class, () -> catalog.adminUpdate(1L, null, -1));
        assertEquals(40001, ex.getCode());
        assertEquals(before, books.selectById(1L).getStock());
    }
}
