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

    /** 队列名。 */
    public static final String QUEUE = "bookmall.notification.create";

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
