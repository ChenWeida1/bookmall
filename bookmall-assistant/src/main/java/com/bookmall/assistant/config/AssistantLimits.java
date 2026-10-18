package com.bookmall.assistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 客服提问的长度和历史窗口。默认值与改配置之前一致。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bookmall.assistant")
public class AssistantLimits {

    /** 最近消息条数，一轮最多两条。 */
    private int historyMessages = 20;

    /** 用户提问最长字数。 */
    private int maxQuestion = 2000;

    /** 入库的回复上限。 */
    private int maxStored = 16000;

    /** 流式响应允许的时间，略长于模型读取超时。 */
    private Duration streamTimeout = Duration.ofSeconds(90);
}
