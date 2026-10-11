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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知的查询、标为已读和内部创建。
 * <p>
 * 用户侧列表不返回 userId。内部创建接口的响应会多带 userId，方便订单服务核对写入结果。
 * 标题最长 64，正文最长 255，空白内容按参数不合法处理。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 通知表。 */
    private final NotificationMapper notifications;

    /**
     * 分页查询当前用户的通知，新的在前。
     *
     * @param userId 当前用户
     * @param page   页码，缺省 1
     * @param size   每页条数，缺省 10
     * @return 通知分页，read 为布尔值
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
     *
     * @param userId 当前用户
     * @param id     通知主键
     * @return 更新后的通知，read 为 true
     */
    @Transactional
    public Map<String, Object> markRead(long userId, long id) {
        Notification notification = notifications.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId));
        if (notification == null) {
            throw BusinessException.notFound("通知不存在");
        }
        notification.setReadFlag((short) 1);
        notifications.updateById(notification);
        return view(notification);
    }

    /**
     * 由订单服务创建一条未读通知。
     *
     * @param userId  收件用户，必须为正数
     * @param title   标题
     * @param content 正文
     * @return 新建通知，额外包含 userId
     */
    @Transactional
    public Map<String, Object> create(Long userId, String title, String content) {
        if (userId == null || userId <= 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        String safeTitle = trimRequired(title, 64);
        String safeContent = trimRequired(content, 255);
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(safeTitle);
        notification.setContent(safeContent);
        notification.setReadFlag((short) 0);
        notifications.insert(notification);
        Map<String, Object> body = view(notification);
        body.put("userId", notification.getUserId());
        return body;
    }

    /** 用户可见字段。readFlag 为 1 时 read 为 true。 */
    private Map<String, Object> view(Notification notification) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", notification.getId());
        row.put("title", notification.getTitle());
        row.put("content", notification.getContent());
        row.put("read", notification.getReadFlag() != null && notification.getReadFlag() == 1);
        row.put("createdAt", Texts.time(notification.getCreatedAt()));
        return row;
    }

    /** 去掉首尾空白，并拒绝空串和超长文本。 */
    private static String trimRequired(String raw, int max) {
        if (raw == null || raw.isBlank() || raw.trim().length() > max) {
            throw BusinessException.badRequest("参数不合法");
        }
        return raw.trim();
    }
}
