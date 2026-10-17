package com.bookmall.assistant.config;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 按配置创建 LangChain4j 的流式模型。不使用会在启动时强制要密钥的自动配置。
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    /**
     * 指向智谱的 OpenAI 兼容地址。请求和响应都不打日志，避免密钥进入日志。
     * 连接超时 5 秒，读取超时 60 秒。
     */
    @Bean
    @Conditional(ApiKeyPresentCondition.class)
    StreamingChatModel streamingChatModel(LlmProperties properties) {
        JdkHttpClientBuilder http = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60));
        return OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(60))
                .httpClientBuilder(http)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /** 补写梗概用的非流式模型。同样不记录请求，避免密钥进日志。 */
    @Bean
    @Conditional(ApiKeyPresentCondition.class)
    ChatModel chatModel(LlmProperties properties) {
        JdkHttpClientBuilder http = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30));
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.3)
                .maxTokens(512)
                .timeout(Duration.ofSeconds(30))
                .httpClientBuilder(http)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
