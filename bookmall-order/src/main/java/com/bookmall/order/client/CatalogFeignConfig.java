package com.bookmall.order.client;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * 图书 Feign 的超时。不要加 {@code @Configuration}，避免被组件扫描后作用到全部客户端。
 */
public class CatalogFeignConfig {

    /**
     * 连接和读取超时来自配置，默认都是 3 秒。
     * Feign 的子容器没有 Spring Boot 的时长转换，这里自己转。
     */
    @Bean
    Request.Options catalogRequestOptions(
            @Value("${bookmall.catalog-connect-timeout:3s}") String connectTimeout,
            @Value("${bookmall.catalog-read-timeout:3s}") String readTimeout) {
        return new Request.Options(duration(connectTimeout), duration(readTimeout), true);
    }

    private static Duration duration(String value) {
        return ApplicationConversionService.getSharedInstance().convert(value, Duration.class);
    }
}
