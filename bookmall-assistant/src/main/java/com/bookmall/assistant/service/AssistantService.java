package com.bookmall.assistant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.assistant.config.AssistantLimits;
import com.bookmall.assistant.config.LlmProperties;
import com.bookmall.assistant.entity.ChatRecord;
import com.bookmall.assistant.mapper.ChatRecordMapper;
import com.bookmall.assistant.tool.BookTools;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.PageQuery;
import com.bookmall.common.web.Texts;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客服历史和流式提问。
 * <p>
 * 没密钥时提问直接返回 50301，不写入消息，也不调用模型。
 * 有密钥时先保存用户消息，再把最近若干条交给模型。流结束或中途断开都保存已生成的回复。
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatRecordMapper records;

    private final LlmProperties llm;

    private final AssistantLimits limits;

    private final ObjectProvider<StreamingChatModel> models;

    private final BookTools bookTools;

    /**
     * 分页返回当前用户的时间线。第 1 页是最近的消息，页内按时间从早到晚。
     */
    public PageResult<Map<String, Object>> history(long userId, Integer page, Integer size) {
        PageQuery query = PageQuery.of(page, size);
        Page<ChatRecord> result = records.selectPage(new Page<>(query.page(), query.size()),
                Wrappers.<ChatRecord>lambdaQuery()
                        .eq(ChatRecord::getUserId, userId)
                        .orderByDesc(ChatRecord::getCreatedAt)
                        .orderByDesc(ChatRecord::getId));
        List<ChatRecord> rows = new ArrayList<>(result.getRecords());
        Collections.reverse(rows);
        List<Map<String, Object>> items = rows.stream().map(this::view).toList();
        return new PageResult<>(query.page(), query.size(), result.getTotal(), items);
    }

    /**
     * 发起一次流式提问。未配置密钥时抛出 50301，不落库。
     */
    public SseEmitter ask(long userId, JsonNode body) {
        if (!llm.configured() || models.getIfAvailable() == null) {
            throw new BusinessException(503, 50301, "客服未配置");
        }
        String question = readQuestion(body);
        List<ChatRecord> earlier = recent(userId);
        insert(userId, ChatRecord.USER, question);

        StreamingChatModel model = models.getIfAvailable();
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(limits.getHistoryMessages() + 1);
        for (ChatRecord row : earlier) {
            if (ChatRecord.ASSISTANT.equals(row.getRole())) {
                memory.add(AiMessage.from(row.getContent()));
            } else {
                memory.add(UserMessage.from(row.getContent()));
            }
        }
        BookChat chat = AiServices.builder(BookChat.class)
                .streamingChatModel(model)
                .chatMemory(memory)
                .tools(bookTools)
                .build();

        SseEmitter emitter = new SseEmitter(limits.getStreamTimeout().toMillis());
        StringBuilder partial = new StringBuilder();
        Object lock = new Object();
        AtomicBoolean stored = new AtomicBoolean(false);
        Runnable save = () -> savePartial(userId, partial, lock, stored);

        emitter.onCompletion(save);
        emitter.onTimeout(save);
        emitter.onError(error -> save.run());

        TokenStream stream = chat.ask(question);
        stream.onPartialResponse(token -> {
                    synchronized (lock) {
                        partial.append(token);
                    }
                    send(emitter, "delta", Map.of("text", token));
                })
                .onCompleteResponse(response -> {
                    String full = response == null || response.aiMessage() == null ? "" : response.aiMessage().text();
                    synchronized (lock) {
                        if (full != null && !full.isBlank()) {
                            partial.setLength(0);
                            partial.append(full);
                        }
                    }
                    save.run();
                    String content;
                    synchronized (lock) {
                        content = partial.toString();
                    }
                    send(emitter, "done", Map.of("content", content));
                    emitter.complete();
                })
                .onError(error -> {
                    log.error("客服回答失败 userId={} type={}", userId, error.getClass().getSimpleName());
                    save.run();
                    send(emitter, "error", Map.of("message", "服务器内部错误"));
                    emitter.complete();
                })
                .start();
        return emitter;
    }

    /** 只保存一次。空白回复不入库。 */
    private void savePartial(long userId, StringBuilder partial, Object lock, AtomicBoolean stored) {
        String text;
        synchronized (lock) {
            if (!stored.compareAndSet(false, true)) {
                return;
            }
            text = partial.toString();
        }
        if (text.isBlank()) {
            return;
        }
        insert(userId, ChatRecord.ASSISTANT, text);
    }

    private void insert(long userId, String role, String content) {
        ChatRecord row = new ChatRecord();
        row.setUserId(userId);
        row.setRole(role);
        row.setContent(limit(content));
        records.insert(row);
    }

    /** 最近若干条，按时间从早到晚，不含即将写入的这条提问。 */
    private List<ChatRecord> recent(long userId) {
        List<ChatRecord> latest = records.selectList(Wrappers.<ChatRecord>lambdaQuery()
                .eq(ChatRecord::getUserId, userId)
                .orderByDesc(ChatRecord::getId)
                .last("LIMIT " + limits.getHistoryMessages()));
        List<ChatRecord> ordered = new ArrayList<>(latest);
        Collections.reverse(ordered);
        return ordered;
    }

    private Map<String, Object> view(ChatRecord row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("role", row.getRole());
        item.put("content", row.getContent());
        item.put("createdAt", Texts.time(row.getCreatedAt()));
        return item;
    }

    private String readQuestion(JsonNode body) {
        if (body == null || !body.has("content") || !body.get("content").isTextual()) {
            throw BusinessException.badRequest("参数不合法");
        }
        String text = body.get("content").asText().trim();
        if (text.isEmpty()) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (text.length() > limits.getMaxQuestion()) {
            throw BusinessException.badRequest("内容长度不能超过 " + limits.getMaxQuestion() + " 个字符");
        }
        return text;
    }

    private String limit(String content) {
        if (content.length() <= limits.getMaxStored()) {
            return content;
        }
        return content.substring(0, limits.getMaxStored());
    }

    private static void send(SseEmitter emitter, String name, Map<String, String> data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            emitter.complete();
        }
    }
}
