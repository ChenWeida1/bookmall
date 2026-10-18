package com.bookmall.catalog.service;

import com.bookmall.catalog.config.CatalogCacheProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 分类与图书列表缓存。
 * <p>
 * TTL 来自配置，避免长期脏数据。管理端改书或库存变化后调用 {@link #evictBooks()} 清列表相关 key。
 * Redis 不可用时降级读库，不向外抛 500。
 */
@Component
@RequiredArgsConstructor
public class BookCache {

    private static final Logger log = LoggerFactory.getLogger(BookCache.class);

    /** 分类列表固定 key。 */
    public static final String KEY_CATEGORIES = "bookmall:catalog:categories";

    /** 图书列表 key 前缀，完整形如 bookmall:catalog:books:p1:s10:c-:k-。 */
    public static final String KEY_BOOKS_PREFIX = "bookmall:catalog:books:";

    private final RedisTemplate<String, Object> redis;

    private final CatalogCacheProperties cache;

    /**
     * 读缓存，未命中则执行 loader 并回写；Redis 异常则直接 loader。
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Supplier<T> loader) {
        try {
            Object cached = redis.opsForValue().get(key);
            if (cached != null) {
                return (T) cached;
            }
        } catch (RuntimeException ex) {
            log.warn("读缓存失败，降级查库 key={} reason={}", key, ex.toString());
            return loader.get();
        }
        T value = loader.get();
        try {
            redis.opsForValue().set(key, value, cache.getBookTtl());
        } catch (RuntimeException ex) {
            log.warn("写缓存失败 key={} reason={}", key, ex.toString());
        }
        return value;
    }

    /** 图书列表缓存 key：页码、每页、分类、关键词。 */
    public static String booksKey(int page, int size, Long categoryId, String keyword) {
        String c = categoryId == null ? "-" : String.valueOf(categoryId);
        String k = keyword == null || keyword.isBlank() ? "-" : keyword.trim().toLowerCase();
        return KEY_BOOKS_PREFIX + "p" + page + ":s" + size + ":c" + c + ":k" + k;
    }

    /**
     * 管理端改书后清空分类与图书列表缓存。
     * 用 pattern 删除 books:*，分类单独删。
     */
    public void evictBooks() {
        try {
            redis.delete(KEY_CATEGORIES);
            Set<String> keys = redis.keys(KEY_BOOKS_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("清缓存失败 reason={}", ex.toString());
        }
    }
}
