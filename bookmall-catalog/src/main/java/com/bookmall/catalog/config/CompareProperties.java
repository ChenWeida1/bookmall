package com.bookmall.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 进货对照的上限。每次对照读取当前值，Nacos 刷新后下一笔请求就用新值。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.compare")
public class CompareProperties {

    /** 一次最多对照的书名数。 */
    private int maxTitles = 20;

    /** 同时打到数据库和百科的上限。 */
    private int parallelism = 4;

    /** 整批对照的上限。单本百科超时另计。 */
    private Duration deadline = Duration.ofSeconds(20);

    /** 等待查询名额的时间。拿不到就这一行不可用。 */
    private Duration permitWait = Duration.ofSeconds(15);
}
