package com.bookmall.assistant.service;

import com.bookmall.assistant.client.CatalogQueryClient;
import com.bookmall.assistant.config.LlmProperties;
import com.bookmall.common.api.ApiResponse;
import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 没有梗概时用智谱写一份并存回图书服务。已有管理员或百科正文时直接返回。
 */
@Service
@RequiredArgsConstructor
public class SynopsisService {

    private final CatalogQueryClient catalog;

    private final LlmProperties llm;

    private final ObjectProvider<ChatModel> models;

    private final ObjectMapper objectMapper;

    /** 读取店内梗概。没有时补写一次。 */
    public String ensure(long bookId) {
        if (bookId <= 0) {
            return "图书不存在";
        }
        try {
            ApiResponse<Map<String, Object>> body = catalog.content(bookId);
            if (body == null || body.getCode() == 40400 || body.getData() == null) {
                return "图书不存在";
            }
            Map<String, Object> data = body.getData();
            Object existing = data.get("synopsis");
            if (existing != null && !String.valueOf(existing).isBlank()) {
                return String.valueOf(existing);
            }
            if (!configured()) {
                return "客服未配置";
            }
            String text = write(text(data.get("title")), text(data.get("author")), text(data.get("description")));
            catalog.saveSynopsis(bookId, Map.of("synopsis", text));
            return text;
        } catch (FeignException.NotFound ex) {
            return "图书不存在";
        } catch (FeignException ex) {
            return "查询失败";
        }
    }

    /**
     * 按书名或作者查百科。没有价格和库存。
     * 没有词条时说明没有查到。连不上时说明暂时不可用。
     */
    public String external(String title, String author) {
        String query = firstText(title, author);
        if (query == null) {
            return "没有查到符合条件的图书";
        }
        try {
            ApiResponse<Map<String, Object>> body = catalog.external(query);
            if (body == null || body.getCode() != 0 || body.getData() == null) {
                return "没有查到符合条件的图书";
            }
            Map<String, Object> data = new LinkedHashMap<>(body.getData());
            data.remove("price");
            data.remove("stock");
            data.remove("status");
            boolean inStore = Boolean.TRUE.equals(data.remove("inStore"));
            Object existing = data.get("synopsis");
            if (existing == null || String.valueOf(existing).isBlank()) {
                if (!configured()) {
                    return inStore ? "本店已上架，没有梗概" : "本店未上架，没有梗概";
                }
                String text = write(text(data.get("title")), text(data.get("author")), "");
                Object lemma = data.get("lemmaId");
                if (lemma != null) {
                    try {
                        catalog.saveExternalSynopsis(Long.parseLong(String.valueOf(lemma)), Map.of("synopsis", text));
                    } catch (FeignException ex) {
                        if (ex.status() == 503) {
                            return "内容库暂时不可用";
                        }
                        return "查询失败";
                    }
                }
                data.put("synopsis", text);
                data.put("source", "ZHIPU");
            }
            String prefix = inStore ? "本店已上架\n" : "本店未上架\n";
            return prefix + objectMapper.writeValueAsString(data);
        } catch (FeignException ex) {
            if (ex.status() == 503) {
                return "外部书目暂时不可用";
            }
            return "查询失败";
        } catch (BusinessException ex) {
            if (ex.getCode() == 50301) {
                return "客服未配置";
            }
            return "查询失败";
        } catch (JsonProcessingException | NumberFormatException ex) {
            return "查询失败";
        }
    }

    /** 管理端按钮。没密钥时抛出 50301，不写内容。 */
    public String ensureOrThrow(long bookId) {
        if (!configured()) {
            throw new BusinessException(503, 50301, "客服未配置");
        }
        String text = ensure(bookId);
        if ("客服未配置".equals(text)) {
            throw new BusinessException(503, 50301, "客服未配置");
        }
        if ("图书不存在".equals(text)) {
            throw BusinessException.notFound("图书不存在");
        }
        if ("查询失败".equals(text)) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        return text;
    }

    private boolean configured() {
        return llm.configured() && models.getIfAvailable() != null;
    }

    private String write(String title, String author, String description) {
        ChatModel model = models.getIfAvailable();
        if (model == null) {
            throw new BusinessException(503, 50301, "客服未配置");
        }
        String prompt = """
                请用中文写一段 200 字以内的图书梗概。
                只根据下面的书名、作者和已有简介来写。
                不要写价格、库存或是否在售。
                书名：%s
                作者：%s
                简介：%s
                """.formatted(empty(title), empty(author), empty(description));
        String text = model.chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                .aiMessage()
                .text();
        if (text == null || text.isBlank()) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        String trimmed = text.trim();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) : trimmed;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private static String firstText(String title, String author) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (author != null && !author.isBlank()) {
            return author.trim();
        }
        return null;
    }
}
