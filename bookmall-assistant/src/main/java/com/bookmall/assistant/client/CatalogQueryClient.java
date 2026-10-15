package com.bookmall.assistant.client;

import com.bookmall.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
