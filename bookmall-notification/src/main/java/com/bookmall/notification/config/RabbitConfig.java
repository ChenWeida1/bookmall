package com.bookmall.notification.config;

import com.bookmall.common.mq.NotificationMessage;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 通知服务声明与订单服务相同的交换机/队列，保证消费者可独立启动。
 * 主队列带死信参数：消费端有限次重试后拒绝且不重新入队，消息进入死信队列。
 * test 配置文件下不声明，避免单元测试强依赖本机 RabbitMQ。
 */
@Configuration
@Profile("!test")
public class RabbitConfig {

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NotificationMessage.EXCHANGE, true, false);
    }

    /** 与订单服务的声明保持一致，含死信交换机和路由键。 */
    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NotificationMessage.QUEUE)
                .deadLetterExchange(NotificationMessage.DLX)
                .deadLetterRoutingKey(NotificationMessage.DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    DirectExchange notificationDeadExchange() {
        return new DirectExchange(NotificationMessage.DLX, true, false);
    }

    @Bean
    Queue notificationDeadQueue() {
        return QueueBuilder.durable(NotificationMessage.DLQ).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NotificationMessage.ROUTING_KEY);
    }

    @Bean
    Binding notificationDeadBinding(Queue notificationDeadQueue, DirectExchange notificationDeadExchange) {
        return BindingBuilder.bind(notificationDeadQueue).to(notificationDeadExchange)
                .with(NotificationMessage.DLQ_ROUTING_KEY);
    }

    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
