package com.bookmall.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 签发和校验登录令牌。
 * <p>
 * 使用 HMAC-SHA 签名。主题（subject）存用户编号，自定义声明 {@code username}、{@code role}。
 * 网关和各业务服务必须使用同一密钥。
 */
public class JwtService {

    /** 由配置中的共享密钥派生出的签名密钥。 */
    private final SecretKey key;

    /**
     * @param secret {@code bookmall.jwt.secret}
     */
    public JwtService(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param userId    用户主键
     * @param username  登录名
     * @param role      1 读者 / 2 管理员
     * @param expiresAt 过期时间
     */
    public String create(long userId, String username, short role, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("role", (int) role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并还原用户。旧令牌若无 role 声明，按读者处理。
     */
    public JwtUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number roleNum = claims.get("role", Number.class);
        short role = roleNum == null ? JwtUser.ROLE_READER : roleNum.shortValue();
        return new JwtUser(Long.parseLong(claims.getSubject()), claims.get("username", String.class), role);
    }
}
