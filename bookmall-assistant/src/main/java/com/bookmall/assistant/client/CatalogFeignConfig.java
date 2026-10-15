package com.bookmall.assistant.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * 客服查书的超时。不要加 {@code @Configuration}，避免作用到其他客户端。
 */
public class CatalogFeignConfig {

    /** 连接 3 秒，读取 5 秒。 */
    @Bean
    Request.Options catalogRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }
}
