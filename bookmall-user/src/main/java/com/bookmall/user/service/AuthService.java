package com.bookmall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.user.entity.UserAccount;
import com.bookmall.user.mapper.UserAccountMapper;
import com.bookmall.common.auth.JwtService;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.Texts;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册、登录和当前用户查询。
 * <p>
 * 用户名去掉首尾空白后长度为 3–20，且不能含空白字符。密码长度为 6–32，按原文校验，不裁剪。
 * 登录失败不区分用户名不存在和密码错误。令牌有效期 24 小时。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户表。 */
    private final UserAccountMapper users;

    /** 密码哈希与比对。 */
    private final PasswordEncoder passwordEncoder;

    /** 令牌签发。 */
    private final JwtService jwtService;

    /**
     * 注册新用户并立即签发令牌。
     * <p>
     * 先按用户名查重。并发下若仍撞上唯一约束，同样返回“用户名已存在”。
     * 昵称为空时使用用户名。
     *
     * @param rawUsername 原始用户名
     * @param password    明文密码
     * @param rawNickname 原始昵称，可空
     * @return 令牌和用户摘要
     */
    @Transactional
    public Map<String, Object> register(String rawUsername, String password, String rawNickname) {
        String username = normalizeUsername(rawUsername);
        validatePassword(password);
        String nickname = normalizeNickname(rawNickname, username);
        if (users.selectCount(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, username)) > 0) {
            throw new BusinessException(400, 40002, "用户名已存在");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setNickname(nickname);
        try {
            users.insert(account);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(400, 40002, "用户名已存在");
        }
        return tokenResponse(account);
    }

    /**
     * 校验用户名和密码后签发令牌。
     *
     * @param rawUsername 原始用户名
     * @param password    明文密码
     * @return 令牌和用户摘要
     */
    public Map<String, Object> login(String rawUsername, String password) {
        String username = normalizeUsername(rawUsername);
        validatePassword(password);
        UserAccount account = users.selectOne(Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, username));
        // 账号不存在和密码不匹配返回同一错误，避免暴露用户名是否已注册。
        if (account == null || !passwordEncoder.matches(password, account.getPassword())) {
            throw new BusinessException(400, 40003, "用户名或密码错误");
        }
        return tokenResponse(account);
    }

    /**
     * 查询当前登录用户的公开资料。
     *
     * @param userId 令牌中的用户主键
     * @return id、username、nickname
     */
    public Map<String, Object> me(long userId) {
        UserAccount account = users.selectById(userId);
        if (account == null) {
            throw BusinessException.unauthorized();
        }
        return userView(account);
    }

    /** 组装注册或登录成功后的令牌响应，过期时间截到秒。 */
    private Map<String, Object> tokenResponse(UserAccount account) {
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwtService.create(account.getId(), account.getUsername(), expiresAt));
        body.put("tokenType", "Bearer");
        body.put("expiresAt", Texts.time(expiresAt.atOffset(java.time.ZoneOffset.UTC)));
        body.put("user", userView(account));
        return body;
    }

    /** 对外返回的用户摘要，不包含密码哈希。 */
    private Map<String, Object> userView(UserAccount account) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.getId());
        user.put("username", account.getUsername());
        user.put("nickname", account.getNickname());
        return user;
    }

    /**
     * 去掉首尾空白并检查用户名长度与空白字符。
     *
     * @param raw 原始用户名
     * @return 规范化后的用户名
     */
    static String normalizeUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.badRequest("用户名和密码不能为空");
        }
        String username = raw.trim();
        if (containsWhitespace(username)) {
            throw BusinessException.badRequest("用户名不能包含空白字符");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw BusinessException.badRequest("用户名长度须为 3–20 个字符");
        }
        return username;
    }

    /**
     * 检查密码非空且长度在 6–32。不裁剪，避免把用户输入的空格悄悄丢掉。
     *
     * @param password 明文密码
     */
    static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw BusinessException.badRequest("用户名和密码不能为空");
        }
        if (password.length() < 6 || password.length() > 32) {
            throw BusinessException.badRequest("密码长度须为 6–32 个字符");
        }
    }

    /** 昵称为空时回退为用户名，非空时去掉首尾空白并限制 32 个字符。 */
    private static String normalizeNickname(String raw, String username) {
        if (raw == null || raw.isBlank()) {
            return username;
        }
        String nickname = raw.trim();
        if (nickname.length() > 32) {
            throw BusinessException.badRequest("昵称长度不能超过 32 个字符");
        }
        return nickname;
    }

    /** 用户名中间也不允许空白，trim 之后仍要逐字符检查。 */
    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
