package com.bookmall.order.config;

import com.bookmall.common.mq.NotificationMessage;
import com.bookmall.order.mq.PayTimeoutMessage;
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
 * 订单服务侧声明交换机与队列，保证首次启动即可投递。
 * 通知队列的死信参数必须与通知服务一致，否则后启动的一方会声明失败。
 * 支付超时用延迟队列的消息级过期时间，过期后经死信进入超时队列。
 * test 配置文件下不声明，避免单元测试强依赖本机 RabbitMQ。
 */
@Configuration
@Profile("!test")
public class RabbitConfig {

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(NotificationMessage.EXCHANGE, true, false);
    }

    /** 主通知队列。瞬时失败被拒绝且不重新入队时，转入死信队列。 */
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
    DirectExchange payTimeoutExchange() {
        return new DirectExchange(PayTimeoutMessage.EXCHANGE, true, false);
    }

    /**
     * 延迟队列。过期时间写在消息上，队列本身不带 TTL，改配置不必删队列。
     * 过期后按死信路由键进入超时队列。
     */
    @Bean
    Queue payDelayQueue() {
        return QueueBuilder.durable(PayTimeoutMessage.DELAY_QUEUE)
                .deadLetterExchange(PayTimeoutMessage.EXCHANGE)
                .deadLetterRoutingKey(PayTimeoutMessage.TIMEOUT_KEY)
                .build();
    }

    /** 超时关单队列。处理失败超过重试次数后进入自己的死信队列。 */
    @Bean
    Queue payTimeoutQueue() {
        return QueueBuilder.durable(PayTimeoutMessage.TIMEOUT_QUEUE)
                .deadLetterExchange(PayTimeoutMessage.DLX)
                .deadLetterRoutingKey(PayTimeoutMessage.DLQ_KEY)
                .build();
    }

    @Bean
    DirectExchange payTimeoutDeadExchange() {
        return new DirectExchange(PayTimeoutMessage.DLX, true, false);
    }

    @Bean
    Queue payTimeoutDeadQueue() {
        return QueueBuilder.durable(PayTimeoutMessage.DLQ).build();
    }

    @Bean
    Binding payDelayBinding(Queue payDelayQueue, DirectExchange payTimeoutExchange) {
        return BindingBuilder.bind(payDelayQueue).to(payTimeoutExchange).with(PayTimeoutMessage.DELAY_KEY);
    }

    @Bean
    Binding payTimeoutBinding(Queue payTimeoutQueue, DirectExchange payTimeoutExchange) {
        return BindingBuilder.bind(payTimeoutQueue).to(payTimeoutExchange).with(PayTimeoutMessage.TIMEOUT_KEY);
    }

    @Bean
    Binding payTimeoutDeadBinding(Queue payTimeoutDeadQueue, DirectExchange payTimeoutDeadExchange) {
        return BindingBuilder.bind(payTimeoutDeadQueue).to(payTimeoutDeadExchange).with(PayTimeoutMessage.DLQ_KEY);
    }

    /** JSON 序列化消息体，便于跨服务阅读。 */
    @Bean
    MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
