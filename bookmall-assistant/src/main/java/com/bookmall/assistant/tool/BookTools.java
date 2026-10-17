package com.bookmall.assistant.tool;

import com.bookmall.assistant.client.CatalogQueryClient;
import com.bookmall.assistant.service.SynopsisService;
import com.bookmall.common.api.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服可调用的只读工具。没有下单、支付、取消。
 */
@Component
@RequiredArgsConstructor
public class BookTools {

    private static final List<String> FIELDS = List.of(
            "id", "title", "author", "price", "stock", "status", "categoryName");

    private final CatalogQueryClient catalog;

    private final ObjectMapper objectMapper;

    private final SynopsisService synopses;

    /**
     * 按书名、作者或分类查书，结果里带价格、库存和在售状态。
     */
    @Tool("按书名、作者或分类查找图书，包含已下架的书。返回价格、库存和 ON_SALE 或 OFF_SALE。没有查到就说明没有。")
    public String searchBooks(
            @P("书名关键词，可空") String title,
            @P("作者关键词，可空") String author,
            @P("分类名称，可空") String category) {
        try {
            ApiResponse<List<Map<String, Object>>> body = catalog.search(blankToNull(title), blankToNull(author), blankToNull(category));
            if (body == null || body.getCode() != 0 || body.getData() == null || body.getData().isEmpty()) {
                return "没有查到符合条件的图书";
            }
            List<Map<String, Object>> brief = body.getData().stream().map(this::brief).toList();
            return objectMapper.writeValueAsString(brief);
        } catch (FeignException | JsonProcessingException ex) {
            return "查询失败";
        }
    }

    /** 读取一本图书的价格、库存和在售状态。 */
    @Tool("按图书编号读取价格、库存和在售状态。编号不存在就说明图书不存在。")
    public String bookDetail(@P("图书编号") long bookId) {
        if (bookId <= 0) {
            return "图书不存在";
        }
        try {
            ApiResponse<Map<String, Object>> body = catalog.detail(bookId);
            if (body == null || body.getCode() != 0 || body.getData() == null) {
                return "图书不存在";
            }
            return objectMapper.writeValueAsString(brief(body.getData()));
        } catch (FeignException.NotFound ex) {
            return "图书不存在";
        } catch (FeignException | JsonProcessingException ex) {
            return "查询失败";
        }
    }

    /** 读取梗概。没有时补写并存库，再把正文返回。 */
    @Tool("按图书编号读取梗概、目录和摘录。没有梗概时会生成一段并保存。编号不存在就说明图书不存在。")
    public String bookSynopsis(@P("图书编号") long bookId) {
        return synopses.ensure(bookId);
    }

    /** 查百科。结果里没有价格和库存。 */
    @Tool("按书名或作者查询本店未上架的外部图书梗概。结果没有价格和库存。没有查到就说明没有。")
    public String searchExternalBooks(
            @P("书名，可空") String title,
            @P("作者，可空") String author) {
        return synopses.external(title, author);
    }

    /** 只留下客服需要的字段，避免把简介整段塞进模型。 */
    private Map<String, Object> brief(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String field : FIELDS) {
            if (source.containsKey(field)) {
                row.put(field, source.get(field));
            }
        }
        return row;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
