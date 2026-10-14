package com.bookmall.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部补发通知的用户编号必须是整数，不能把 1.5 截成 1。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationCreateTest {

    @Autowired
    MockMvc mvc;

    @Test
    void fractionalUserIdIsRejected() throws Exception {
        mvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1.5,"title":"订单创建成功","content":"小数用户编号不能入库"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void integerUserIdIsAccepted() throws Exception {
        mvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"title":"订单创建成功","content":"整数用户编号可以入库"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1));
    }
}
