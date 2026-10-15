package com.bookmall.assistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 客服对话的一条消息，对应表 {@code assistant_message}。
 * 一个用户只有一条时间线，用 {@code userId} 区分，不另建会话表。
 */
@Getter
@Setter
@TableName("assistant_message")
public class ChatRecord {

    /** 用户发出的消息。 */
    public static final String USER = "user";

    /** 客服的回复。 */
    public static final String ASSISTANT = "assistant";

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 这条消息属于哪个用户。 */
    private Long userId;

    /** {@code user} 或 {@code assistant}。 */
    private String role;

    /** 消息正文。 */
    private String content;

    /** 写入时间，UTC。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
