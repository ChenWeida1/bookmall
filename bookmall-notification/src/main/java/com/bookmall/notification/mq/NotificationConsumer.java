package com.bookmall.notification.mq;

import com.bookmall.common.error.BusinessException;
import com.bookmall.common.mq.NotificationMessage;
import com.bookmall.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notifications;

    @RabbitListener(queues = NotificationMessage.QUEUE)
    public void onMessage(NotificationMessage message) {
        if (message == null) {
            return;
        }
        try {
            notifications.create(message.getUserId(), message.getTitle(), message.getContent(), message.getBizKey());
        } catch (BusinessException ex) {
            // 参数不合法时记日志后正常返回，容器自动确认，避免毒消息反复入队。
            // 数据库等瞬时失败继续抛出，由容器拒绝并重新投递。
            log.error("消费通知失败 userId={} bizKey={} reason={}",
                    message.getUserId(), message.getBizKey(), ex.toString());
        }
    }
}
