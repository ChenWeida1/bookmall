package com.bookmall.common.auth;

/**
 * 从 JWT 中解析出的登录用户。
 *
 * @param userId   用户主键
 * @param username 登录名
 * @param role     角色：1 读者，2 管理员；缺省按读者处理
 */
public record JwtUser(long userId, String username, short role) {

    /** 读者。 */
    public static final short ROLE_READER = 1;

    /** 管理员。 */
    public static final short ROLE_ADMIN = 2;

    /** 是否管理员。 */
    public boolean admin() {
        return role == ROLE_ADMIN;
    }
}
