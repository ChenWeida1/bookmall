package com.bookmall.order.service;

import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 死信队列为空时，Rabbit 包装后的业务异常仍要回到 40001，不能变成 500。
 */
class PayTimeoutDeadLetterServiceTest {

    @Test
    void emptyQueueStaysBadRequest() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(rabbit.execute(any())).thenThrow(new AmqpException("wrapped", BusinessException.badRequest("没有可处理的死信")));
        PayTimeoutDeadLetterService service = new PayTimeoutDeadLetterService(rabbit, new ObjectMapper());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.act("discard"));
        assertEquals(40001, ex.getCode());
        assertEquals("没有可处理的死信", ex.getMessage());
    }
}
