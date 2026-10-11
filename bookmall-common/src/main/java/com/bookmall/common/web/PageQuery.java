package com.bookmall.common.web;

import com.bookmall.common.error.BusinessException;

/**
 * 已校验的分页参数。
 * <p>
 * 缺省页码为 1，缺省每页 10 条。页码必须大于等于 1，每页条数必须在 1 到 50 之间，
 * 超出范围按参数不合法处理。数据库查询使用从 0 开始的偏移，由 {@link #offset()} 换算。
 *
 * @param page 页码，从 1 开始
 * @param size 每页条数
 */
public record PageQuery(int page, int size) {

    /**
     * 把可空的请求参数规范成合法分页。
     *
     * @param page 请求中的页码，null 时按 1
     * @param size 请求中的每页条数，null 时按 10
     * @return 校验后的分页
     * @throws BusinessException 页码或每页条数超出允许范围
     */
    public static PageQuery of(Integer page, Integer size) {
        int p = page == null ? 1 : page;
        int s = size == null ? 10 : size;
        if (p < 1 || s < 1 || s > 50) {
            throw BusinessException.badRequest("参数不合法");
        }
        return new PageQuery(p, s);
    }

    /**
     * 换算成 SQL 的偏移量。
     *
     * @return {@code (page - 1) * size}
     */
    public int offset() {
        return (page - 1) * size;
    }
}
