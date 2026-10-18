package com.bookmall.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 智谱 OpenAI 兼容接口的配置。密钥只留在服务端。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.llm")
public class LlmProperties {

    /** 接口密钥。空字符串表示未配置。 */
    private String apiKey = "";

    /** 智谱 OpenAI 兼容地址。 */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** 模型名。只使用这一个，不自动切换。 */
    private String model = "glm-4.7";

    /** 采样温度。 */
    private double temperature = 0.3;

    /** 单次回答上限。 */
    private int maxTokens = 2048;

    /** 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 流式读取超时。 */
    private Duration streamTimeout = Duration.ofSeconds(60);

    /** 补写梗概的读取超时。 */
    private Duration chatTimeout = Duration.ofSeconds(30);

    /** 补写梗概的生成上限，比对话短。 */
    private int synopsisMaxTokens = 512;

    /** 密钥去掉空白后仍有内容才算已配置。 */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
