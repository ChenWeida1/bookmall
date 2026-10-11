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
 * 使用 HMAC-SHA 签名。主题（subject）存用户编号，自定义声明 {@code username} 存登录名。
 * 网关和各业务服务必须使用同一密钥，否则互相签发的令牌无法通过校验。
 */
public class JwtService {

    /** 由配置中的共享密钥派生出的签名密钥。 */
    private final SecretKey key;

    /**
     * 用配置密钥初始化签名器。密钥长度需满足 HMAC-SHA 的要求。
     *
     * @param secret {@code bookmall.jwt.secret}
     */
    public JwtService(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发一张在指定时刻过期的令牌。
     *
     * @param userId    用户主键，写入 subject
     * @param username  登录名
     * @param expiresAt 过期时间
     * @return 紧凑格式的 JWT 字符串
     */
    public String create(long userId, String username, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名、过期时间并还原用户信息。
     *
     * @param token 不含 {@code Bearer } 前缀的令牌
     * @return 令牌中的用户
     * @throws io.jsonwebtoken.JwtException 签名无效、已过期或格式错误
     */
    public JwtUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtUser(Long.parseLong(claims.getSubject()), claims.get("username", String.class));
    }
}
