package com.bookmall.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.catalog.entity.Book;
import com.bookmall.catalog.mapper.BookMapper;
import com.bookmall.common.auth.JwtService;
import com.bookmall.common.auth.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 校验这次收紧的入参：小数数量不能截成整数，管理端非法库存用 40001。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    BookMapper books;

    @Autowired
    JwtService jwt;

    /** 1.5 本不能被当成 1 本扣掉。 */
    @Test
    void fractionalQuantityDoesNotDeduct() throws Exception {
        int before = books.selectById(1L).getStock();
        mvc.perform(post("/internal/books/1/stock/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1.5,\"token\":\"fractional-qty\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));
        assertEquals(before, books.selectById(1L).getStock());
    }

    /** 整数数量仍然可以扣减。 */
    @Test
    void integerQuantityStillDeducts() throws Exception {
        int before = books.selectById(1L).getStock();
        mvc.perform(post("/internal/books/1/stock/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"token\":\"integer-qty\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(before - 1, books.selectById(1L).getStock());
    }

    /** 管理端库存不是整数时返回 40001，不改库存。 */
    @Test
    void fractionalAdminStockIsBadRequest() throws Exception {
        int before = books.selectById(1L).getStock();
        mvc.perform(put("/api/admin/books/1")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        assertEquals(before, books.selectById(1L).getStock());
    }

    /** 客服内部查询能看到下架书；三个条件都空时不返回全表。 */
    @Test
    void assistantSearchIncludesOffSaleAndSkipsBlankQuery() throws Exception {
        mvc.perform(get("/internal/books/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
        books.update(null, Wrappers.<Book>lambdaUpdate().set(Book::getOnSale, false).eq(Book::getId, 1L));
        mvc.perform(get("/internal/books/search").param("title", "围城"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("围城"))
                .andExpect(jsonPath("$.data[0].status").value("OFF_SALE"))
                .andExpect(jsonPath("$.data[0].price").exists())
                .andExpect(jsonPath("$.data[0].stock").exists());
    }

    /** 空白上架状态不能当成修改成功。 */
    @Test
    void blankAdminStatusIsBadRequest() throws Exception {
        mvc.perform(put("/api/admin/books/1")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    /** 读者不能改上下架。 */
    @Test
    void readerCannotUpdateBook() throws Exception {
        mvc.perform(put("/api/admin/books/1")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_READER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SALE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private String token(short role) {
        return jwt.create(1L, "tester", role, Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
