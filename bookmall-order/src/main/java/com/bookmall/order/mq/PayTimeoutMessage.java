package com.bookmall.order.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 待支付订单的超时关单消息。
 * <p>
 * 下单成功后发到延迟队列，消息过期后由死信转入超时队列。
 * 消费者只在订单仍是待支付时取消并还库存。
 * 空构造供 JSON 反序列化，全参构造供投递时组装。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayTimeoutMessage {

    /** 支付超时交换机。延迟队列和超时队列都绑在这里。 */
    public static final String EXCHANGE = "bookmall.order.pay";

    /** 延迟队列。不设队列级 TTL，由每条消息的过期时间决定。 */
    public static final String DELAY_QUEUE = "bookmall.order.pay.delay";

    /** 延迟路由键。 */
    public static final String DELAY_KEY = "pay.delay";

    /** 超时队列。延迟消息过期后进入此队列。 */
    public static final String TIMEOUT_QUEUE = "bookmall.order.pay.timeout";

    /** 超时路由键，也是延迟队列的死信路由键。 */
    public static final String TIMEOUT_KEY = "pay.timeout";

    /** 超时处理失败时的死信交换机。 */
    public static final String DLX = "bookmall.order.pay.dlx";

    /** 超时处理失败超过重试次数后进入的队列。 */
    public static final String DLQ = "bookmall.order.pay.timeout.dlq";

    /** 超时死信路由键。 */
    public static final String DLQ_KEY = "pay.timeout.dlq";

    private long orderId;
    private long userId;
}
