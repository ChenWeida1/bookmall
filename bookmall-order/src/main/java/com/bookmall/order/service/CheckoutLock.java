package com.bookmall.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 把同一用户的下单串行化。
 * <p>
 * 锁挂在当前事务上，提交或回滚时自动放开。第二个请求会等到第一个请求清空购物车之后再读购物车。
 */
@Component
@RequiredArgsConstructor
public class CheckoutLock {

    /** 使用当前事务里的数据库连接。 */
    private final JdbcTemplate jdbc;

    /**
     * 锁住该用户，直到调用方所在的事务结束。
     *
     * @param userId 下单用户
     */
    public void lock(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {
        }, userId);
    }
}
