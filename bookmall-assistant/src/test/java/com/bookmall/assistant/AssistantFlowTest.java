package com.bookmall.assistant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.assistant.entity.ChatRecord;
import com.bookmall.assistant.mapper.ChatRecordMapper;
import com.bookmall.assistant.service.AssistantService;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客服历史只属于当前用户。没密钥时提问是 50301，并且不落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssistantFlowTest {

    @Autowired
    AssistantService assistant;

    @Autowired
    ChatRecordMapper records;

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void missingKeyReturns50301AndWritesNothing() throws Exception {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> assistant.ask(910201L, json.readTree("{\"content\":\"有围城吗\"}")));
        assertEquals(50301, ex.getCode());
        assertEquals(503, ex.getHttpStatus());
        assertEquals("客服未配置", ex.getMessage());
        assertEquals(0, records.selectCount(Wrappers.<ChatRecord>lambdaQuery().eq(ChatRecord::getUserId, 910201L)));
    }

    @Test
    void historyFirstPageIsTheNewestTen() {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC);
        for (int i = 1; i <= 11; i++) {
            ChatRecord row = new ChatRecord();
            row.setUserId(910204L);
            row.setRole(i % 2 == 1 ? ChatRecord.USER : ChatRecord.ASSISTANT);
            row.setContent("第" + i + "条");
            row.setCreatedAt(start.plusMinutes(i));
            records.insert(row);
        }
        PageResult<Map<String, Object>> first = assistant.history(910204L, 1, 10);
        assertEquals(11, first.getTotal());
        assertEquals(10, first.getItems().size());
        assertEquals("第2条", first.getItems().get(0).get("content"));
        assertEquals("第11条", first.getItems().get(9).get("content"));
        PageResult<Map<String, Object>> second = assistant.history(910204L, 2, 10);
        assertEquals(1, second.getItems().size());
        assertEquals("第1条", second.getItems().get(0).get("content"));
    }

    @Test
    void historyBelongsToTheCurrentUser() {
        ChatRecord row = new ChatRecord();
        row.setUserId(910202L);
        row.setRole(ChatRecord.USER);
        row.setContent("你好");
        records.insert(row);
        assertEquals(0, assistant.history(910203L, 1, 10).getTotal());
        assertTrue(assistant.history(910202L, 1, 10).getItems().stream()
                .anyMatch(item -> "你好".equals(item.get("content"))));
        assertTrue(assistant.history(910203L, 1, 10).getItems().stream()
                .noneMatch(item -> "你好".equals(item.get("content"))));
    }

    @Test
    void messagesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/assistant/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
