package com.bookmall.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bookmall.catalog.baike.BaikeCard;
import com.bookmall.catalog.content.BookContentService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    /** 分类与图书列表缓存。 */
    private final BookCache cache;

    /** 梗概与百科词条。读失败不影响价格和库存。 */
    private final BookContentService contents;

    /**
     * 列出全部分类，按主键升序。结果缓存 60 秒。
     *
     * @return 每项含 id、name
     */
    public List<Map<String, Object>> listCategories() {
        return cache.getOrLoad(BookCache.KEY_CATEGORIES, this::loadCategories);
    }

    private List<Map<String, Object>> loadCategories() {
        return categories.selectList(Wrappers.<Category>lambdaQuery().orderByAsc(Category::getId)).stream().map(category -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.getId());
            row.put("name", category.getName());
            return row;
        }).toList();
    }

    /**
     * 分页查询在售图书。分类不存在时返回空页。列表结果按页条件缓存。
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
        String key = BookCache.booksKey(query.page(), query.size(), categoryId, keyword);
        return cache.getOrLoad(key, () -> loadBooks(query, categoryId, keyword));
    }

    /**
     * 客服内部查书。公开列表只含在售书，这里连下架书一起返回，并带上价格、库存和状态。
     * 书名、作者、分类都空时不扫全表，直接返回空列表。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchForAssistant(String title, String author, String category) {
        String titlePattern = likePattern(title);
        String authorPattern = likePattern(author);
        String categoryPattern = likePattern(category);
        if (titlePattern == null && authorPattern == null && categoryPattern == null) {
            return List.of();
        }
        return books.searchForAssistant(titlePattern, authorPattern, categoryPattern).stream().map(book -> {
            Map<String, Object> item = listItem(book);
            item.put("status", Book.statusName(book.getOnSale()));
            return item;
        }).toList();
    }

    private PageResult<Map<String, Object>> loadBooks(PageQuery query, Long categoryId, String keyword) {
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
        item.put("isbn", book.getIsbn());
        item.put("status", Book.statusName(book.getOnSale()));
        contents.overlay(item, book.getId());
        return item;
    }

    /**
     * 管理员新增图书。必填分类、书名、作者、价格、库存和上下架。
     */
    @Transactional
    public Map<String, Object> adminCreate(Map<String, Object> body) {
        Book book = new Book();
        long categoryId = requiredLong(body.get("categoryId"));
        if (categories.selectById(categoryId) == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        book.setCategoryId(categoryId);
        book.setTitle(requiredText(body.get("title"), 128));
        book.setAuthor(requiredText(body.get("author"), 64));
        book.setPrice(requiredPrice(body.get("price")));
        Integer stock = whole(body.get("stock"));
        if (stock == null || stock < 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        book.setStock(stock);
        book.setOnSale(parseStatus(requiredText(body.get("status"), 16)));
        book.setCoverUrl(optionalText(body.get("coverUrl"), 255));
        book.setDescription(optionalText(body.get("description"), 4000));
        book.setIsbn(optionalText(body.get("isbn"), 32));
        books.insert(book);
        evictBooksAfterChange();
        contents.saveAdminExtras(book.getId(), optionalText(body.get("synopsis"), 4000), stringList(body.get("tags")));
        return detail(book.getId());
    }

    /** 管理员改梗概、目录、摘录、标签和作者简介。 */
    @Transactional
    public Map<String, Object> adminContent(long id, Map<String, Object> body) {
        if (id <= 0 || books.selectById(id) == null) {
            throw BusinessException.notFound("图书不存在");
        }
        contents.replaceAdmin(id,
                optionalText(body.get("synopsis"), 4000),
                optionalText(body.get("contents"), 4000),
                optionalText(body.get("excerpt"), 4000),
                stringList(body.get("tags")),
                optionalText(body.get("authorBio"), 4000));
        return detail(id);
    }

    /** 管理员或客服按词查一条百科。没有词条时返回 null。 */
    public Map<String, Object> searchExternal(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        return contents.searchExternal(keyword.trim());
    }

    /** 用已搜索到的词条新建可售图书，并绑上内容文档。 */
    @Transactional
    public Map<String, Object> adminImport(Map<String, Object> body) {
        Object rawLemma = body.get("lemmaId");
        long lemmaId = requiredLong(rawLemma);
        BaikeCard card = contents.cardForImport(lemmaId);
        long categoryId = requiredLong(body.get("categoryId"));
        if (categories.selectById(categoryId) == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        Integer stock = whole(body.get("stock"));
        if (stock == null || stock < 0 || card.title() == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        Book book = new Book();
        book.setCategoryId(categoryId);
        book.setTitle(clip(card.title(), 128));
        book.setAuthor(card.author() == null ? "未知" : clip(card.author(), 64));
        book.setPrice(requiredPrice(body.get("price")));
        book.setStock(stock);
        book.setOnSale(parseStatus(requiredText(body.get("status"), 16)));
        book.setCoverUrl(card.coverUrl());
        book.setDescription(card.description() == null ? card.synopsis() : card.description());
        books.insert(book);
        evictBooksAfterChange();
        contents.bindImported(book.getId(), card);
        return detail(book.getId());
    }

    /** 客服读取一本书的内容和短简介。 */
    public Map<String, Object> contentForAssistant(long id) {
        Book book = books.selectById(id);
        if (id <= 0 || book == null) {
            throw BusinessException.notFound("图书不存在");
        }
        return contents.viewForAssistant(book);
    }

    /** 客服把智谱梗概写入内容库。已有管理员或百科正文时不覆盖。 */
    public Map<String, Object> saveZhipuSynopsis(long id, String synopsis) {
        Book book = books.selectById(id);
        if (id <= 0 || book == null) {
            throw BusinessException.notFound("图书不存在");
        }
        return contents.saveZhipu(book, synopsis);
    }

    /** 给没有正文的百科词条写入智谱梗概。已有管理员或百科正文时不覆盖。 */
    public Map<String, Object> saveZhipuLemma(long lemmaId, String synopsis) {
        if (lemmaId <= 0) {
            throw BusinessException.notFound("图书不存在");
        }
        return contents.saveZhipuLemma(lemmaId, synopsis);
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
        evictBooksAfterChange();
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
        if (Boolean.FALSE.equals(existing.getDeducted())) {
            return;
        }
        if (books.restore(id, qty) != 1) {
            throw BusinessException.notFound("图书不存在");
        }
        if (changes.markRestored(safeToken) != 1) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        evictBooksAfterChange();
    }

    /**
     * 订单取消时按数量加回库存，不依赖下单时的扣减令牌。
     * <p>
     * 与带 token 的 restore 分开，避免取消路径误改 stock_change 表。
     */
    @Transactional
    public void restoreQuantity(long id, Integer quantity) {
        int qty = requirePositive(quantity);
        if (id <= 0 || books.selectById(id) == null) {
            throw BusinessException.notFound("图书不存在");
        }
        if (books.restore(id, qty) != 1) {
            throw BusinessException.notFound("图书不存在");
        }
        evictBooksAfterChange();
    }

    /**
     * 管理员修改上架状态，可选覆盖库存。
     * status 仅接受 ON_SALE / OFF_SALE（或 1 / 0）。
     */
    @Transactional
    public Map<String, Object> adminUpdate(long id, String statusName, Integer stock) {
        if (id <= 0) {
            throw BusinessException.notFound("图书不存在");
        }
        Book book = books.selectById(id);
        if (book == null) {
            throw BusinessException.notFound("图书不存在");
        }
        boolean statusProvided = statusName != null;
        if (!statusProvided && stock == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        Boolean onSale = null;
        if (statusProvided) {
            if (statusName.isBlank()) {
                throw BusinessException.badRequest("参数不合法");
            }
            onSale = parseStatus(statusName);
        }
        if (stock != null && stock < 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (onSale != null) {
            books.updateStatus(id, onSale);
        }
        if (stock != null && books.updateStock(id, stock) != 1) {
            throw BusinessException.notFound("图书不存在");
        }
        evictBooksAfterChange();
        return detail(id);
    }

    /**
     * 库存或上下架变了就清列表缓存。
     * <p>
     * 事务还没提交时，别的请求可能读到旧库存并写回缓存。提交成功后再清一次，避免这 60 秒内列表仍显示旧库存。
     */
    private void evictBooksAfterChange() {
        cache.evictBooks();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evictBooks();
            }
        });
    }

    /** 接受 ON_SALE/OFF_SALE 或 1/0。库里存布尔值。 */
    private static boolean parseStatus(String raw) {
        String value = raw.trim();
        if ("ON_SALE".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("OFF_SALE".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw BusinessException.badRequest("参数不合法");
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
                || !Boolean.TRUE.equals(existing.getDeducted())) {
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

    private static long requiredLong(Object value) {
        Integer number = whole(value);
        if (number == null || number <= 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        return number;
    }

    private static Integer whole(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer number) {
            return number;
        }
        if (value instanceof Long number) {
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw BusinessException.badRequest("参数不合法");
            }
            return number.intValue();
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            if (!Double.isFinite(raw) || raw != Math.floor(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
                throw BusinessException.badRequest("参数不合法");
            }
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("参数不合法");
        }
    }

    private static String requiredText(Object value, int max) {
        String text = optionalText(value, max);
        if (text == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        return text;
    }

    private static String optionalText(Object value, int max) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw BusinessException.badRequest("参数不合法");
        }
        return text;
    }

    private static BigDecimal requiredPrice(Object value) {
        if (value == null) {
            throw BusinessException.badRequest("参数不合法");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(String.valueOf(value).trim()).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (price.scale() < 0) {
            price = price.setScale(0);
        }
        if (price.scale() > 2 || price.compareTo(BigDecimal.ZERO) < 0 || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw BusinessException.badRequest("参数不合法");
        }
        return price.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw BusinessException.badRequest("参数不合法");
        }
        List<String> tags = new ArrayList<>();
        for (Object item : list) {
            if (item == null || String.valueOf(item).isBlank()) {
                continue;
            }
            String tag = String.valueOf(item).trim();
            if (tag.length() > 32) {
                throw BusinessException.badRequest("参数不合法");
            }
            tags.add(tag);
        }
        return tags;
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /** 扣减数量必须是正整数。 */
    private static int requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(400, 40007, "数量不合法");
        }
        return quantity;
    }
}
