package com.bookmall.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 百度百科名片。appid 是公开示例，超时和地址可以热刷新。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.baike")
public class BaikeProperties {

    /** 公开示例 appid，也可用环境变量 BAIKE_APP_ID 覆盖。 */
    private String appId = "379020";

    /** 名片接口地址。 */
    private String cardUrl = "https://baike.baidu.com/api/openapi/BaikeLemmaCardApi";

    /** 连接和读取超时。 */
    private Duration timeout = Duration.ofSeconds(3);

    /** 摘要长度，对应请求参数 bk_length。 */
    private int excerptLength = 600;
}
