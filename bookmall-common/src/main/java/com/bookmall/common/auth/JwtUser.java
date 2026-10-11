package com.bookmall.common.auth;

/**
 * 从 JWT 中解析出的登录用户。
 *
 * @param userId   用户主键
 * @param username 登录名
 */
public record JwtUser(long userId, String username) {
}
