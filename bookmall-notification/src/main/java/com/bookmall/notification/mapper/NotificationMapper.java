package com.bookmall.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知表访问。查询都带用户编号，不能读到别人的消息。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
