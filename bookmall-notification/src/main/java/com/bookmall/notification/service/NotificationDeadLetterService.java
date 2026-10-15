package com.bookmall.notification.service;

import com.bookmall.common.error.BusinessException;
import com.bookmall.common.mq.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用 basicGet 查看和处理通知死信，不挂常驻消费者。
 * 重投发回交换机 {@code bookmall.notification}、路由键 {@code notification.create}，由现有消费者按 bizKey 幂等入库。
 */
@Service
@RequiredArgsConstructor
public class NotificationDeadLetterService {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /** 查看积压条数和队头消息。读出后立刻重新入队，不确认。 */
    public Map<String, Object> inspect() {
        return execute(channel -> {
            AMQP.Queue.DeclareOk declared = channel.queueDeclarePassive(NotificationMessage.DLQ);
            GetResponse got = channel.basicGet(NotificationMessage.DLQ, false);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("queue", NotificationMessage.DLQ);
            body.put("count", declared.getMessageCount());
            if (got == null) {
                body.put("head", null);
                return body;
            }
            body.put("head", readBody(got));
            channel.basicNack(got.getEnvelope().getDeliveryTag(), false, true);
            return body;
        });
    }

    /** 处理最早的一条。replay 重投到通知队列，discard 直接确认丢弃。 */
    public Map<String, Object> act(String action) {
        boolean discard = "discard".equals(action);
        boolean replay = "replay".equals(action);
        if (!discard && !replay) {
            throw BusinessException.badRequest("参数不合法");
        }
        return execute(channel -> {
            GetResponse got = channel.basicGet(NotificationMessage.DLQ, false);
            if (got == null) {
                throw BusinessException.badRequest("没有可处理的死信");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("queue", NotificationMessage.DLQ);
            body.put("action", action);
            body.put("message", readBody(got));
            if (replay) {
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .build();
                channel.basicPublish(NotificationMessage.EXCHANGE, NotificationMessage.ROUTING_KEY, props, got.getBody());
            }
            channel.basicAck(got.getEnvelope().getDeliveryTag(), false);
            return body;
        });
    }

    /** RabbitTemplate 会把回调里的业务异常包进 AMQP 异常，这里拆出来仍按 40001 返回。 */
    private Map<String, Object> execute(org.springframework.amqp.rabbit.core.ChannelCallback<Map<String, Object>> callback) {
        try {
            Map<String, Object> result = rabbitTemplate.execute(callback);
            if (result == null) {
                throw new BusinessException(500, 50000, "服务器内部错误");
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Throwable cursor = ex;
            while (cursor != null) {
                if (cursor instanceof BusinessException business) {
                    throw business;
                }
                cursor = cursor.getCause();
            }
            throw ex;
        }
    }

    private Object readBody(GetResponse got) {
        String json = new String(got.getBody(), StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }
}
