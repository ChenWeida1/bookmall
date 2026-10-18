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

/**
 * 按配置创建 LangChain4j 的流式模型。不使用会在启动时强制要密钥的自动配置。
 */
@Configuration
@EnableConfigurationProperties({LlmProperties.class, AssistantLimits.class})
public class LlmConfiguration {

    /**
     * 指向智谱的 OpenAI 兼容地址。请求和响应都不打日志，避免密钥进入日志。
     * 连接和读取超时来自配置。
     */
    @Bean
    @Conditional(ApiKeyPresentCondition.class)
    StreamingChatModel streamingChatModel(LlmProperties properties) {
        JdkHttpClientBuilder http = new JdkHttpClientBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getStreamTimeout());
        return OpenAiStreamingChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(properties.getStreamTimeout())
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
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getChatTimeout());
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getSynopsisMaxTokens())
                .timeout(properties.getChatTimeout())
                .httpClientBuilder(http)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
