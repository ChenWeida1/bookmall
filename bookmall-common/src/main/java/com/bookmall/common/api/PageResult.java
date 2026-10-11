package com.bookmall.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询结果。
 * <p>
 * {@code page} 从 1 开始，与请求参数一致。{@code total} 是符合条件的总条数，不是当前页条数。
 * 空构造供 JSON 反序列化，全参构造供查询结果组装。
 *
 * @param <T> 列表元素类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页码，从 1 开始。 */
    private int page;

    /** 每页条数。 */
    private int size;

    /** 符合条件的总条数。 */
    private long total;

    /** 当前页的数据。 */
    private List<T> items;
}
