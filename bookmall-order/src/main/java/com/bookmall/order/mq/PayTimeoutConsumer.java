package com.bookmall.order.mq;

import com.bookmall.common.error.BusinessException;
import com.bookmall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 消费支付超时消息。订单已支付或已取消时直接确认，避免重复还库存。
 * 还库存等瞬时失败继续抛出，由监听器有限次重试后进入死信队列。
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class PayTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(PayTimeoutConsumer.class);

    private final OrderService orders;

    @RabbitListener(queues = PayTimeoutMessage.TIMEOUT_QUEUE)
    public void onMessage(PayTimeoutMessage message) {
        if (message == null || message.getOrderId() <= 0 || message.getUserId() <= 0) {
            return;
        }
        try {
            orders.expireIfPending(message.getUserId(), message.getOrderId());
        } catch (BusinessException ex) {
            if (ex.getCode() != 40008) {
                // 还库存失败等 50000 继续抛出，让监听器有限次重试后再进死信。
                throw ex;
            }
            // 状态已不允许取消时记日志后确认，避免同一条超时消息反复入队。
            log.error("超时关单未执行 orderId={} userId={} reason={}",
                    message.getOrderId(), message.getUserId(), ex.toString());
        }
    }
}
