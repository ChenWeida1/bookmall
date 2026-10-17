package com.bookmall.assistant.client;

import com.bookmall.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 直连图书服务查书。只读，不提供下单、支付或取消。
 * <p>
 * {@code url} 有值时直连。为空时按服务名走本地注册中心。
 */
@FeignClient(name = "bookmall-catalog", url = "${bookmall.catalog-base-url:}", configuration = CatalogFeignConfig.class)
public interface CatalogQueryClient {

    /** 内部查询，包含下架书。 */
    @GetMapping("/internal/books/search")
    ApiResponse<List<Map<String, Object>>> search(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "category", required = false) String category);

    /** 单本详情。下架书也能读到状态。 */
    @GetMapping("/api/books/{id}")
    ApiResponse<Map<String, Object>> detail(@PathVariable("id") long id);

    /** 梗概和短简介。没有内容文档时梗概为空。 */
    @GetMapping("/internal/books/{id}/content")
    ApiResponse<Map<String, Object>> content(@PathVariable("id") long id);

    /** 写入智谱梗概。已有正文时服务端不覆盖。 */
    @PutMapping("/internal/books/{id}/synopsis")
    ApiResponse<Map<String, Object>> saveSynopsis(@PathVariable("id") long id, @RequestBody Map<String, String> body);

    /** 按词查百科。没有词条时 data 为 null。 */
    @GetMapping("/internal/books/external")
    ApiResponse<Map<String, Object>> external(@RequestParam("q") String q);

    /** 给没有正文的百科词条写入智谱梗概。已有正文时服务端不覆盖。 */
    @PutMapping("/internal/books/external/{lemmaId}/synopsis")
    ApiResponse<Map<String, Object>> saveExternalSynopsis(
            @PathVariable("lemmaId") long lemmaId,
            @RequestBody Map<String, String> body);
}
