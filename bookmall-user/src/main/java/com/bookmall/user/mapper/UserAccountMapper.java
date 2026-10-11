package com.bookmall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账号表 {@code user_account} 的 MyBatis-Plus 访问。
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
