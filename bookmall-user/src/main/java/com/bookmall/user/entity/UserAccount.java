package com.bookmall.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 用户账号，对应表 {@code user_account}。
 * <p>
 * {@code password} 只存 BCrypt 哈希。昵称为空时由注册逻辑回退成用户名。
 * {@code createdAt} 在插入时由 {@link com.bookmall.user.config.CreatedAtFillHandler} 写成 UTC。
 */
@Getter
@Setter
@TableName("user_account")
public class UserAccount {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，长度 3–20，全局唯一。 */
    private String username;

    /** BCrypt 哈希后的密码，不是明文。 */
    private String password;

    /** 展示用昵称，缺省与用户名相同。 */
    private String nickname;

    /** 注册时间，UTC。插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
