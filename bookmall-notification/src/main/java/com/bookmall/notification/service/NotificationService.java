package com.bookmall.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.notification.entity.Notification;
import com.bookmall.notification.mapper.NotificationMapper;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.PageQuery;
import com.bookmall.common.web.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知的查询、标为已读和创建（含 MQ 消费与内部补发）。
 * <p>
 * 用户侧列表不返回 userId。带 {@code bizKey} 时按唯一约束幂等：重复写入视为成功。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 通知表。 */
    private final NotificationMapper notifications;

    /**
     * 分页查询当前用户的通知，新的在前。
     */
    public PageResult<Map<String, Object>> list(long userId, Integer page, Integer size) {
        PageQuery query = PageQuery.of(page, size);
        Page<Notification> result = notifications.selectPage(new Page<>(query.page(), query.size()),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));
        return new PageResult<>(query.page(), query.size(), result.getTotal(),
                result.getRecords().stream().map(this::view).toList());
    }

    /**
     * 把一条通知标为已读。重复标记仍返回成功后的内容。
     */
    @Transactional
    public Map<String, Object> markRead(long userId, long id) {
        Notification notification = notifications.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId));
        if (notification == null) {
            throw BusinessException.notFound("通知不存在");
        }
        notification.setReadFlag(true);
        notifications.updateById(notification);
        return view(notification);
    }

    /**
     * 内部 HTTP 补发：无 bizKey。
     */
    @Transactional
    public Map<String, Object> create(Long userId, String title, String content) {
        return create(userId, title, content, null);
    }

    /**
     * 创建未读通知。若提供 bizKey 且已存在，直接返回已有记录（幂等）。
     */
    @Transactional
    public Map<String, Object> create(Long userId, String title, String content, String bizKey) {
        if (userId == null || userId <= 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        String safeTitle = trimRequired(title, 64);
        String safeContent = trimRequired(content, 255);
        String safeBizKey = trimBizKey(bizKey);
        if (safeBizKey != null) {
            Notification existing = notifications.selectOne(Wrappers.<Notification>lambdaQuery()
                    .eq(Notification::getBizKey, safeBizKey));
            if (existing != null) {
                Map<String, Object> body = view(existing);
                body.put("userId", existing.getUserId());
                return body;
            }
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(safeTitle);
        notification.setContent(safeContent);
        notification.setReadFlag(false);
        notification.setBizKey(safeBizKey);
        try {
            notifications.insert(notification);
        } catch (DuplicateKeyException ex) {
            // 并发重复投递：再查一次已有行。
            if (safeBizKey != null) {
                Notification existing = notifications.selectOne(Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getBizKey, safeBizKey));
                if (existing != null) {
                    Map<String, Object> body = view(existing);
                    body.put("userId", existing.getUserId());
                    return body;
                }
            }
            throw ex;
        }
        Map<String, Object> body = view(notification);
        body.put("userId", notification.getUserId());
        return body;
    }

    /** 用户可见字段。readFlag 为 true 时 read 为 true。 */
    private Map<String, Object> view(Notification notification) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", notification.getId());
        row.put("title", notification.getTitle());
        row.put("content", notification.getContent());
        row.put("read", Boolean.TRUE.equals(notification.getReadFlag()));
        row.put("createdAt", Texts.time(notification.getCreatedAt()));
        return row;
    }

    private static String trimBizKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 64) {
            throw BusinessException.badRequest("参数不合法");
        }
        return value;
    }

    /** 去掉首尾空白，并拒绝空串和超长文本。 */
    private static String trimRequired(String raw, int max) {
        if (raw == null || raw.isBlank() || raw.trim().length() > max) {
            throw BusinessException.badRequest("参数不合法");
        }
        return raw.trim();
    }
}
