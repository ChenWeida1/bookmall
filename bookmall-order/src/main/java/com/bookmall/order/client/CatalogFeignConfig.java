package com.bookmall.order.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 图书 Feign 的超时。不要加 {@code @Configuration}，避免被组件扫描后作用到全部客户端。
 */
public class CatalogFeignConfig {

    /** 连接和读取都是 3 秒，与原来的 RestClient 一致。 */
    @Bean
    Request.Options catalogRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }
}
