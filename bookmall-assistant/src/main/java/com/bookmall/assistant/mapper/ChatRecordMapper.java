package com.bookmall.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.assistant.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;

/** 客服消息表。 */
@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}
