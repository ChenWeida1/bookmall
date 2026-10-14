package com.bookmall.order;

import com.bookmall.common.error.BusinessException;
import com.bookmall.order.mq.PayTimeoutConsumer;
import com.bookmall.order.mq.PayTimeoutMessage;
import com.bookmall.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

/**
 * 超时关单遇到「状态不允许」时确认消息；还库存失败要继续抛出，交给重试和死信。
 */
@ExtendWith(MockitoExtension.class)
class PayTimeoutConsumerTest {

    @Mock
    OrderService orders;

    @Test
    void statusConflictIsAcked() {
        PayTimeoutMessage message = new PayTimeoutMessage(9L, 3L);
        doThrow(new BusinessException(400, 40008, "订单状态不允许取消"))
                .when(orders).expireIfPending(3L, 9L);
        new PayTimeoutConsumer(orders).onMessage(message);
    }

    @Test
    void stockFailureIsRethrown() {
        PayTimeoutMessage message = new PayTimeoutMessage(9L, 3L);
        doThrow(new BusinessException(500, 50000, "服务器内部错误"))
                .when(orders).expireIfPending(3L, 9L);
        assertThrows(BusinessException.class, () -> new PayTimeoutConsumer(orders).onMessage(message));
    }
}
