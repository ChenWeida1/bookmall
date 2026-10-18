package com.bookmall.catalog;

import com.bookmall.catalog.baike.BaikeCard;
import com.bookmall.catalog.config.CompareProperties;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第七期：进货对照用虚拟线程同时查本店和百科。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogPhase7Test {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Autowired
    FakeBaikeClient baike;

    @Autowired
    CompareProperties compare;

    @AfterEach
    void resetBaike() {
        baike.reset();
        compare.setMaxTitles(20);
    }

    @Test
    void readerCannotCompare() throws Exception {
        mvc.perform(post("/api/admin/books/compare")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_READER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titles\":[\"围城\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void blankOrTooManyTitlesAreRejected() throws Exception {
        String admin = token(JwtUser.ROLE_ADMIN);
        mvc.perform(post("/api/admin/books/compare")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titles\":[\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        String tooMany = IntStream.range(0, 21).mapToObj(i -> "\"书" + i + "\"").collect(Collectors.joining(",", "{\"titles\":[", "]}"));
        mvc.perform(post("/api/admin/books/compare")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void compareKeepsOrderAndDoesNotInventExternalPrice() throws Exception {
        baike.script("围城", new BaikeCard(2068378L, "围城", "钱锺书", "百科摘要", null, "http://baike.example/weicheng", null, null));
        baike.script("对照无词条", null);
        baike.scriptUnavailable("对照连不上");
        mvc.perform(post("/api/admin/books/compare")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titles\":[\"围城\",\"对照无词条\",\"对照连不上\",\"围城\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].query").value("围城"))
                .andExpect(jsonPath("$.data.items[0].stores[0].price").exists())
                .andExpect(jsonPath("$.data.items[0].stores[0].stock").value(greaterThan(-1)))
                .andExpect(jsonPath("$.data.items[0].external.synopsis").value("百科摘要"))
                .andExpect(jsonPath("$.data.items[0].external.price").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].external.stock").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].externalStatus").value("OK"))
                .andExpect(jsonPath("$.data.items[1].externalStatus").value("EMPTY"))
                .andExpect(jsonPath("$.data.items[1].external").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].stores", hasSize(0)))
                .andExpect(jsonPath("$.data.items[2].query").value("对照连不上"))
                .andExpect(jsonPath("$.data.items[2].externalStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.items[2].external").value(nullValue()));
        assertTrue(baike.sawVirtualThread());
    }

    /** 最大本数来自配置。改成 1 之后，两本书名在下一笔请求就被拒绝。 */
    @Test
    void configuredMaxTitlesRejectsTheNextRequest() throws Exception {
        compare.setMaxTitles(1);
        mvc.perform(post("/api/admin/books/compare")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titles\":[\"围城\",\"活着\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private String token(short role) {
        return jwt.create(1L, "tester", role, Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
