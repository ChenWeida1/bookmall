package com.bookmall.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 图书服务的 Feign 声明。
 * <p>
 * {@code url} 有值时直连该地址（生产、Kubernetes、测试）。
 * 为空时按服务名 {@code bookmall-catalog} 走本地注册中心。
 */
@FeignClient(name = "bookmall-catalog", url = "${bookmall.catalog-base-url:}", configuration = CatalogFeignConfig.class)
public interface CatalogFeign {

    @GetMapping("/api/books/{id}")
    JsonNode book(@PathVariable("id") long id);

    @PostMapping("/internal/books/{id}/stock/deduct")
    JsonNode deduct(@PathVariable("id") long id, @RequestBody StockBody body);

    @PostMapping("/internal/books/{id}/stock/restore")
    JsonNode restore(@PathVariable("id") long id, @RequestBody StockBody body);

    @PostMapping("/internal/books/{id}/stock/restore-qty")
    JsonNode restoreQuantity(@PathVariable("id") long id, @RequestBody QuantityBody body);

    /** 扣减或按令牌回补的请求体。 */
    record StockBody(int quantity, String token) {
    }

    /** 按数量还库存的请求体。 */
    record QuantityBody(int quantity) {
    }
}
