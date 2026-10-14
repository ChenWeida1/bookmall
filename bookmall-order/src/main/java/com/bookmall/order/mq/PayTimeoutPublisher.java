package com.bookmall.order.mq;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 下单成功后投递待支付超时消息。
 * <p>
 * 过期时间写在消息上，到期后由延迟队列的死信转入超时队列。
 * 投递失败只记日志，不让已经成功的订单回滚。
 */
@Component
@RequiredArgsConstructor
public class PayTimeoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(PayTimeoutPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    /** 待支付保留时间。开发环境可配成 1 分钟，默认 15 分钟。 */
    @Value("${bookmall.pay-timeout:15m}")
    private Duration payTimeout;

    /**
     * 为刚创建的待支付订单安排一次超时检查。
     *
     * @param orderId 订单主键
     * @param userId  下单用户
     */
    public void schedule(long orderId, long userId) {
        PayTimeoutMessage message = new PayTimeoutMessage(orderId, userId);
        try {
            long millis = Math.max(payTimeout.toMillis(), 1L);
            rabbitTemplate.convertAndSend(PayTimeoutMessage.EXCHANGE, PayTimeoutMessage.DELAY_KEY, message, posted -> {
                posted.getMessageProperties().setExpiration(Long.toString(millis));
                return posted;
            });
        } catch (RuntimeException ex) {
            log.error("投递支付超时消息失败 orderId={} userId={} reason={}", orderId, userId, ex.toString());
        }
    }
}
