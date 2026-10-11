package com.bookmall.catalog;

import com.bookmall.catalog.mapper.BookMapper;
import com.bookmall.catalog.service.CatalogService;
import com.bookmall.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
