package com.bookmall.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智谱 OpenAI 兼容接口的配置。密钥只留在服务端。
 */
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /** 密钥去掉空白后仍有内容才算已配置。 */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
