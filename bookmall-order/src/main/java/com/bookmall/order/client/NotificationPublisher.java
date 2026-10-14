package com.bookmall.order.client;

import com.bookmall.common.mq.NotificationMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 通过 RabbitMQ 投递站内信，替代一期同步 HTTP。
 * <p>
 * 发布失败只记日志，不让已成功的订单回滚。通知服务按 {@code bizKey} 幂等入库。
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * 下单成功通知。
     */
    public void orderCreated(long userId, String orderNo, String totalAmount) {
        String content = "您的订单 " + orderNo + " 已创建成功，合计 ¥" + totalAmount;
        publish(userId, "订单创建成功", content, "ORDER_CREATED:" + orderNo, orderNo, totalAmount);
    }

    /**
     * 模拟支付成功通知。不代表真实渠道扣款。
     */
    public void orderPaid(long userId, String orderNo, String totalAmount) {
        String content = "您的订单 " + orderNo + " 已支付，合计 ¥" + totalAmount;
        publish(userId, "订单已支付", content, "ORDER_PAID:" + orderNo, orderNo, totalAmount);
    }

    /**
     * 订单取消通知。
     */
    public void orderCancelled(long userId, String orderNo) {
        String content = "您的订单 " + orderNo + " 已取消，库存已加回。";
        publish(userId, "订单已取消", content, "ORDER_CANCELLED:" + orderNo, orderNo, null);
    }

    private void publish(long userId, String title, String content, String bizKey, String orderNo, String totalAmount) {
        NotificationMessage message = new NotificationMessage(userId, title, content, bizKey);
        try {
            rabbitTemplate.convertAndSend(NotificationMessage.EXCHANGE, NotificationMessage.ROUTING_KEY, message);
        } catch (RuntimeException ex) {
            // 订单已经成功。日志留下订单号和金额，方便以后补发。
            log.error("投递通知失败 orderNo={} userId={} totalAmount={} reason={}",
                    orderNo, userId, totalAmount, ex.toString());
        }
    }
}
