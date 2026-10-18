package com.bookmall.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 同步调用通知服务创建站内信。
 * <p>
 * 第一版不使用消息队列。通知失败不能让已经成功的订单回滚，因此异常只记日志。
 * 超时来自配置，默认 1 秒，避免通知服务变慢拖住下单接口。
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    /**
     * @param baseUrl      通知服务根地址，配置项 {@code bookmall.notification-base-url}
     * @param objectMapper 组装请求体
     */
    public NotificationClient(
            @Value("${bookmall.notification-base-url}") String baseUrl,
            @Value("${bookmall.notification-timeout:1s}") Duration timeout,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 写入一条“订单创建成功”通知。失败时记录订单号、用户和金额，便于事后核对。
     *
     * @param userId      收件用户
     * @param orderNo     订单号
     * @param totalAmount 已格式化的合计金额
     */
    public void create(long userId, String orderNo, String totalAmount) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userId", userId);
        body.put("title", "订单创建成功");
        body.put("content", "您的订单 " + orderNo + " 已创建成功，合计 ¥" + totalAmount);
        try {
            client.post().uri("/internal/notifications").body(body).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.error("创建通知失败 orderNo={} userId={} totalAmount={} reason={}",
                    orderNo, userId, totalAmount, ex.toString());
        }
    }
}
