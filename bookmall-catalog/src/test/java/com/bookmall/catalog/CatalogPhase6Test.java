package com.bookmall.catalog;

import com.bookmall.catalog.baike.BaikeCard;
import com.bookmall.catalog.baike.FakeBaikeClient;
import com.bookmall.common.auth.JwtService;
import com.bookmall.common.auth.JwtUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第六期：新增图书、梗概和百科引进。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogPhase6Test {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Autowired
    FakeBaikeClient baike;

    @AfterEach
    void resetBaike() {
        baike.reset();
    }

    @Test
    void readerCannotCreateBook() throws Exception {
        mvc.perform(post("/api/admin/books")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_READER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("OFF_SALE", "读者不能加的书")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void offSaleBookStaysOutOfListAndKeepsSynopsis() throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/books")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("OFF_SALE", "第六期下架专书")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.synopsis").value("管理员梗概"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id").toString();
        mvc.perform(get("/api/books").param("keyword", "第六期下架专书"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/books/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synopsis").value("管理员梗概"))
                .andExpect(jsonPath("$.data.isbn").value("9780000000001"));
        mvc.perform(put("/internal/books/" + id + "/synopsis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"synopsis\":\"智谱改写\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/books/" + id))
                .andExpect(jsonPath("$.data.synopsis").value("管理员梗概"))
                .andExpect(jsonPath("$.data.source").value("ADMIN"));
    }

    @Test
    void missingLemmaIsEmptyAndDuplicateImportFails() throws Exception {
        mvc.perform(get("/api/admin/books/external").param("q", "没有这本书")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        baike.setNext(new BaikeCard(900001L, "引进测试书", "作者甲", "百科摘要", "第一章", "http://baike.example/1", null, "短简介"));
        mvc.perform(get("/api/admin/books/external").param("q", "引进测试书")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lemmaId").value(900001))
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.stock").doesNotExist());
        String importBody = """
                {"lemmaId":900001,"categoryId":1,"price":12.50,"stock":3,"status":"OFF_SALE"}
                """;
        MvcResult imported = mvc.perform(post("/api/admin/books/import")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("引进测试书"))
                .andExpect(jsonPath("$.data.synopsis").value("百科摘要"))
                .andExpect(jsonPath("$.data.source").value("BAIDU"))
                .andReturn();
        String importedId = com.jayway.jsonpath.JsonPath.read(imported.getResponse().getContentAsString(), "$.data.id").toString();
        mvc.perform(put("/internal/books/" + importedId + "/synopsis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"synopsis\":\"智谱改写\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synopsis").value("百科摘要"));
        mvc.perform(post("/api/admin/books/import")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void emptyLemmaKeepsZhipuAndBaiduSynopsisStays() throws Exception {
        String admin = token(JwtUser.ROLE_ADMIN);
        baike.setNext(new BaikeCard(900002L, "无摘要书", "作者乙", null, null, "http://baike.example/2", null, null));
        mvc.perform(get("/api/admin/books/external").param("q", "无摘要书")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synopsis").value(nullValue()))
                .andExpect(jsonPath("$.data.inStore").value(false))
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.stock").doesNotExist());
        mvc.perform(put("/internal/books/external/900002/synopsis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"synopsis\":\"智谱补写\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synopsis").value("智谱补写"))
                .andExpect(jsonPath("$.data.source").value("ZHIPU"));
        baike.setNext(new BaikeCard(900002L, "无摘要书", "作者乙", null, null, "http://baike.example/2", null, null));
        mvc.perform(get("/api/admin/books/external").param("q", "无摘要书")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.synopsis").value("智谱补写"))
                .andExpect(jsonPath("$.data.source").value("ZHIPU"));

        baike.setNext(new BaikeCard(900003L, "有摘要书", "作者丙", "百科摘要", null, "http://baike.example/3", null, null));
        mvc.perform(get("/api/admin/books/external").param("q", "有摘要书")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.source").value("BAIDU"));
        mvc.perform(put("/internal/books/external/900003/synopsis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"synopsis\":\"智谱改写\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.synopsis").value("百科摘要"))
                .andExpect(jsonPath("$.data.source").value("BAIDU"));
    }

    @Test
    void baikeOutageIs50302() throws Exception {
        baike.setUnavailable(true);
        mvc.perform(get("/api/admin/books/external").param("q", "围城")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50302));
    }

    private static String createBody(String status, String title) {
        return """
                {"categoryId":1,"title":"%s","author":"测试","price":10,"stock":1,"status":"%s","isbn":"9780000000001","synopsis":"管理员梗概"}
                """.formatted(title, status);
    }

    private String token(short role) {
        return jwt.create(1L, "tester", role, Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
