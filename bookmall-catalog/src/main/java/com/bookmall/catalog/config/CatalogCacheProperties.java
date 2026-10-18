package com.bookmall.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 分类和图书列表缓存。TTL 只影响刷新之后新写入的 key。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.cache")
public class CatalogCacheProperties {

    /** 列表缓存过期时间。 */
    private Duration bookTtl = Duration.ofSeconds(60);
}
