package com.bookmall.notification.mq;

import com.bookmall.common.mq.NotificationMessage;
import com.bookmall.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 消费订单服务投递的站内信消息，按 bizKey 幂等入库。
 */
@Component
@Profile("!test")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notifications;

    public NotificationConsumer(NotificationService notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = NotificationMessage.QUEUE)
    public void onMessage(NotificationMessage message) {
        if (message == null) {
            return;
        }
        try {
            notifications.create(message.getUserId(), message.getTitle(), message.getContent(), message.getBizKey());
        } catch (RuntimeException ex) {
            // 业务校验失败（如参数不合法）记日志并 ack，避免毒消息死循环。
            log.error("消费通知失败 userId={} bizKey={} reason={}",
                    message.getUserId(), message.getBizKey(), ex.toString());
        }
    }
}
