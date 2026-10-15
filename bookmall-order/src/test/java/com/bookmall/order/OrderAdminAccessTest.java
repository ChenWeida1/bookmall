package com.bookmall.order;

import com.bookmall.common.auth.JwtService;
import com.bookmall.common.auth.JwtUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端订单接口只允许管理员。读者直连订单服务也要被拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderAdminAccessTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Test
    void readerCannotListAdminOrders() throws Exception {
        mvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_READER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void readerCannotReadDeadLetters() throws Exception {
        mvc.perform(get("/api/admin/orders/dead-letters")
                        .header("Authorization", "Bearer " + token(JwtUser.ROLE_READER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private String token(short role) {
        return jwt.create(7L, "reader", role, Instant.now().plus(1, ChronoUnit.HOURS));
    }
}
