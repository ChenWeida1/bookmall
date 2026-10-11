package com.bookmall.notification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 站内通知，对应表 {@code notification}。
 * <p>
 * 已读标记用数字保存：0 未读，1 已读。接口对外把该数字转成布尔值 {@code read}。
 * {@code createdAt} 在插入时由 {@link com.bookmall.notification.config.CreatedAtFillHandler} 写成 UTC。
 */
@Getter
@Setter
@TableName("notification")
public class Notification {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收件用户。 */
    private Long userId;

    /** 通知标题，最长 64。 */
    private String title;

    /** 通知正文，最长 255。 */
    private String content;

    /** 已读标记：0 未读，1 已读。 */
    private Short readFlag;

    /** 创建时间，UTC。插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
