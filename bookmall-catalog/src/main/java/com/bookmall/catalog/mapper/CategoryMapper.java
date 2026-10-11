package com.bookmall.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bookmall.catalog.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类表 {@code category} 的只读访问。分类按主键升序返回，保证前端筛选顺序稳定。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
