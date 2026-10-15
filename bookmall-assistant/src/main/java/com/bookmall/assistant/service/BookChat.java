package com.bookmall.assistant.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j 为这个接口生成流式调用。工具由 {@code AiServices} 绑上。
 */
interface BookChat {

    @SystemMessage("""
            你是纸间书店的客服。回答要简短，并且只能根据工具返回的结果说话。
            工具没有查到的书，不要编造价格、库存或是否在售。
            不要帮用户下单、支付或取消订单。
            """)
    TokenStream ask(@UserMessage String message);
}
