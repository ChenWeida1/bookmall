package com.bookmall.notification;

import com.bookmall.common.error.BusinessException;
import com.bookmall.common.mq.NotificationMessage;
import com.bookmall.notification.mq.NotificationConsumer;
import com.bookmall.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 参数错误的通知确认后丢弃；数据库等瞬时失败继续抛出，交给监听器重试并最终进入死信队列。
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    NotificationService notifications;

    @Test
    void badMessageIsAcked() {
        NotificationMessage message = new NotificationMessage(1L, "", "内容", "ORDER_CREATED:x");
        when(notifications.create(1L, "", "内容", "ORDER_CREATED:x"))
                .thenThrow(BusinessException.badRequest("参数不合法"));
        new NotificationConsumer(notifications).onMessage(message);
    }

    @Test
    void transientFailureIsRethrown() {
        NotificationMessage message = new NotificationMessage(1L, "订单创建成功", "内容", "ORDER_CREATED:x");
        when(notifications.create(1L, "订单创建成功", "内容", "ORDER_CREATED:x"))
                .thenThrow(new RuntimeException("db down"));
        assertThrows(RuntimeException.class, () -> new NotificationConsumer(notifications).onMessage(message));
    }
}
