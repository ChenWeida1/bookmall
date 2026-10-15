package com.bookmall.notification;

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
 * 通知死信只允许管理员查看。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationDeadLetterAccessTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Test
    void readerCannotReadDeadLetters() throws Exception {
        mvc.perform(get("/api/admin/notifications/dead-letters")
                        .header("Authorization", "Bearer " + jwt.create(7L, "reader", JwtUser.ROLE_READER,
                                Instant.now().plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
