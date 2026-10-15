package com.bookmall.assistant.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 只有配置了密钥才创建模型客户端，避免启动时因为空密钥失败。
 */
public class ApiKeyPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("bookmall.llm.api-key");
        return key != null && !key.isBlank();
    }
}
