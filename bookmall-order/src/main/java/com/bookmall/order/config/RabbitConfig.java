package com.bookmall.order.config;

import com.bookmall.common.mq.NotificationMessage;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 订单服务侧声明交换机与队列，保证首次启动即可投递。
 * 通知服务消费同一队列；两边声明保持一致。
 * test 配置文件下不声明，避免单元测试强依赖本机 RabbitMQ。
 */
@Configuration
@Profile("!test")
public class RabbitConfig {

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NotificationMessage.EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return new Queue(NotificationMessage.QUEUE, true);
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NotificationMessage.ROUTING_KEY);
    }

    /** JSON 序列化消息体，便于跨服务阅读。 */
    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
