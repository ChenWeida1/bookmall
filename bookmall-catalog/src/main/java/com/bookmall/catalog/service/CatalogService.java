package com.bookmall.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.catalog.entity.Book;
import com.bookmall.catalog.entity.Category;
import com.bookmall.catalog.entity.StockChange;
import com.bookmall.catalog.mapper.BookMapper;
import com.bookmall.catalog.mapper.CategoryMapper;
import com.bookmall.catalog.mapper.StockChangeMapper;
import com.bookmall.common.api.PageResult;
import com.bookmall.common.error.BusinessException;
import com.bookmall.common.web.PageQuery;
import com.bookmall.common.web.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类、图书查询和库存变更。
 * <p>
 * 不存在的分类筛选返回空列表，而不是 404，方便前端把“该分类下没有书”和“书不存在”分开。
 * 扣减失败时带回当前库存和请求数量。同一 token 只扣一次；回补按同一 token 加回，重复回补不再加库存。
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    /** 分类表。 */
    private final CategoryMapper categories;

    /** 图书表。 */
    private final BookMapper books;

    /** 扣减令牌。 */
    private final StockChangeMapper changes;

    /**
     * 列出全部分类，按主键升序。
     *
     * @return 每项含 id、name
     */
    public List<Map<String, Object>> listCategories() {
        return categories.selectList(Wrappers.<Category>lambdaQuery().orderByAsc(Category::getId)).stream().map(category -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.getId());
            row.put("name", category.getName());
            return row;
        }).toList();
    }

    /**
     * 分页查询在售图书。分类不存在时返回空页。
     *
     * @param page       页码，缺省 1
     * @param size       每页条数，缺省 10，最大 50
     * @param categoryId 分类主键，可空
     * @param keyword    书名关键词，可空
     * @return 当前页图书摘要
     */
    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> listBooks(Integer page, Integer size, Long categoryId, String keyword) {
        PageQuery query = PageQuery.of(page, size);
        if (categoryId != null && categories.selectById(categoryId) == null) {
            return new PageResult<>(query.page(), query.size(), 0, List.of());
        }
        String pattern = likePattern(keyword);
        Page<Book> result = books.search(new Page<>(query.page(), query.size()), categoryId, pattern);
        List<Map<String, Object>> items = result.getRecords().stream().map(this::listItem).toList();
        return new PageResult<>(query.page(), query.size(), result.getTotal(), items);
    }

    /**
     * 查询单本图书详情。下架书也能查到，状态字段标明是否在售。
     *
     * @param id 图书主键
     * @return 列表字段加上简介、ISBN 占位和状态名
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(long id) {
        if (id <= 0) {
            throw BusinessException.notFound("图书不存在");
        }
        Book book = books.selectById(id);
        if (book == null) {
            throw BusinessException.notFound("图书不存在");
        }
        Category category = categories.selectById(book.getCategoryId());
        book.setCategoryName(category == null ? null : category.getName());
        Map<String, Object> item = listItem(book);
        item.put("description", book.getDescription());
        item.put("isbn", null);
        item.put("status", Book.statusName(book.getStatus()));
        return item;
    }

    /**
     * 扣减库存。更新条件包含“库存不少于数量”，避免并发超卖。
     * <p>
     * 同一 {@code token} 再次调用直接返回当前库存，不再扣减。
     *
     * @param id       图书主键
     * @param quantity 扣减数量，必须为正整数
     * @param token    本轮扣减令牌，由订单服务生成
     * @return 扣减后的 bookId 和 stock
     */
    @Transactional
    public Map<String, Object> deduct(long id, Integer quantity, String token) {
        int qty = requirePositive(quantity);
        String safeToken = requireToken(token);
        if (id <= 0 || books.selectById(id) == null) {
            throw BusinessException.notFound("图书不存在");
        }
        StockChange change = new StockChange();
        change.setToken(safeToken);
        change.setBookId(id);
        change.setQuantity(qty);
        if (changes.insertDeduct(change) == 0) {
            return alreadyDeducted(id, qty, safeToken);
        }
        int updated = books.deduct(id, qty);
        if (updated == 0) {
            throw shortage(id, qty);
        }
        return stockView(id);
    }

    /**
     * 按扣减令牌把库存加回去。
     * <p>
     * 令牌不存在表示扣减没有落地，直接成功。令牌已经回补过则不再加库存。
     *
     * @param id       图书主键
     * @param quantity 回补数量，必须和扣减时一致
     * @param token    扣减时使用的令牌
     */
    @Transactional
    public void restore(long id, Integer quantity, String token) {
        int qty = requirePositive(quantity);
        String safeToken = requireToken(token);
        if (id <= 0) {
            throw BusinessException.notFound("图书不存在");
        }
        StockChange existing = changes.lock(safeToken);
        if (existing == null) {
            return;
        }
        if (!existing.getBookId().equals(id) || existing.getQuantity() != qty) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        if (existing.getKind() != null && existing.getKind() == StockChange.RESTORED) {
            return;
        }
        if (books.restore(id, qty) != 1) {
            throw BusinessException.notFound("图书不存在");
        }
        if (changes.markRestored(safeToken) != 1) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
    }

    /** 列表和详情共用的图书摘要，金额格式化为两位小数。 */
    private Map<String, Object> listItem(Book book) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", book.getId());
        item.put("title", book.getTitle());
        item.put("author", book.getAuthor());
        item.put("coverUrl", book.getCoverUrl());
        item.put("price", Texts.money(book.getPrice()));
        item.put("stock", book.getStock());
        item.put("categoryId", book.getCategoryId());
        item.put("categoryName", book.getCategoryName());
        return item;
    }

    /**
     * 把用户输入转成安全的 LIKE 模式。空白关键词表示不筛选。
     *
     * @param keyword 原始关键词
     * @return 形如 {@code %关键字%} 的模式，空白时为 null
     */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped.toLowerCase() + "%";
    }

    /** 同一令牌已经扣过时，核对图书和数量后返回当前库存。 */
    private Map<String, Object> alreadyDeducted(long id, int quantity, String token) {
        StockChange existing = changes.lock(token);
        if (existing == null
                || !existing.getBookId().equals(id)
                || existing.getQuantity() != quantity
                || existing.getKind() == null
                || existing.getKind() != StockChange.DEDUCTED) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        return stockView(id);
    }

    /** 库存不足时带回当前库存；图书在扣减过程中消失则按不存在处理。 */
    private BusinessException shortage(long id, int quantity) {
        Book book = books.selectById(id);
        if (book == null) {
            return BusinessException.notFound("图书不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookId", id);
        data.put("stock", book.getStock());
        data.put("quantity", quantity);
        return new BusinessException(400, 40006, "库存不足", data);
    }

    /** 扣减成功后的库存。 */
    private Map<String, Object> stockView(long id) {
        Book book = books.selectById(id);
        if (book == null) {
            throw BusinessException.notFound("图书不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookId", id);
        data.put("stock", book.getStock());
        return data;
    }

    /** 扣减令牌由订单服务生成，缺失或超长按参数不合法处理。 */
    private static String requireToken(String token) {
        if (token == null || token.isBlank() || token.trim().length() > 64) {
            throw BusinessException.badRequest("参数不合法");
        }
        return token.trim();
    }

    /** 扣减数量必须是正整数。 */
    private static int requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(400, 40007, "数量不合法");
        }
        return quantity;
    }
}
