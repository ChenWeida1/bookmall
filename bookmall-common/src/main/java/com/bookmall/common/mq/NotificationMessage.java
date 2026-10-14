package com.bookmall.common.mq;

/**
 * 订单服务投递、通知服务消费的站内信消息。
 * <p>
 * {@code bizKey} 用于至少一次投递下的幂等写入，例如 {@code ORDER_CREATED:20261011...}。
 */
public class NotificationMessage {

    /** 交换机名。 */
    public static final String EXCHANGE = "bookmall.notification";

    /** 路由键 / 队列名。 */
    public static final String ROUTING_KEY = "notification.create";

    /** 队列名。声明时带死信参数，瞬时失败超过重试次数后进入死信队列。 */
    public static final String QUEUE = "bookmall.notification.create";

    /** 死信交换机。主队列拒绝且不重新入队时，消息转到这里。 */
    public static final String DLX = "bookmall.notification.dlx";

    /** 死信队列。参数错误的消息不会进入这里，由消费端确认后丢弃。 */
    public static final String DLQ = "bookmall.notification.create.dlq";

    /** 死信路由键。订单服务与通知服务的队列声明必须一致。 */
    public static final String DLQ_ROUTING_KEY = "notification.create.dlq";

    private long userId;
    private String title;
    private String content;
    private String bizKey;

    public NotificationMessage() {
    }

    public NotificationMessage(long userId, String title, String content, String bizKey) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.bizKey = bizKey;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }
}
