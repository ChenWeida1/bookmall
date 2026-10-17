package com.bookmall.catalog.content;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用的内存内容库，不连接 MongoDB。
 */
@Component
@Profile("test")
public class MemoryBookContentStore implements BookContentStore {

    private final AtomicLong sequence = new AtomicLong(1);

    private final ConcurrentHashMap<String, BookContent> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<BookContent> findByBookId(long bookId) {
        return rows.values().stream().filter(row -> Long.valueOf(bookId).equals(row.getBookId())).findFirst();
    }

    @Override
    public Optional<BookContent> findByLemmaId(long lemmaId) {
        return rows.values().stream().filter(row -> Long.valueOf(lemmaId).equals(row.getLemmaId())).findFirst();
    }

    @Override
    public boolean save(BookContent content) {
        BookContent existing = null;
        if (content.getBookId() != null) {
            existing = findByBookId(content.getBookId()).orElse(null);
        }
        if (existing == null && content.getLemmaId() != null) {
            existing = findByLemmaId(content.getLemmaId()).orElse(null);
        }
        if (existing != null) {
            content.setId(existing.getId());
        } else if (content.getId() == null) {
            content.setId("mem-" + sequence.getAndIncrement());
        }
        rows.put(content.getId(), content);
        return true;
    }
}
