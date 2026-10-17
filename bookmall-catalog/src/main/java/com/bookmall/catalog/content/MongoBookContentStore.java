package com.bookmall.catalog.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * 用 MongoDB 保存 {@code book_content}。连接失败时查询当空，写入返回 false。
 */
@Component
@Profile("!test")
public class MongoBookContentStore implements BookContentStore {

    private static final Logger log = LoggerFactory.getLogger(MongoBookContentStore.class);

    private final MongoTemplate template;

    public MongoBookContentStore(MongoTemplate template) {
        this.template = template;
    }

    /** 稀疏唯一索引。建不起来不影响买书。 */
    @PostConstruct
    void ensureIndexes() {
        try {
            template.indexOps(BookContent.class).ensureIndex(new Index().on("bookId", Sort.Direction.ASC).sparse().unique());
            template.indexOps(BookContent.class).ensureIndex(new Index().on("lemmaId", Sort.Direction.ASC).sparse().unique());
        } catch (RuntimeException ex) {
            log.warn("内容索引未建立: {}", ex.toString());
        }
    }

    @Override
    public Optional<BookContent> findByBookId(long bookId) {
        return find("bookId", bookId);
    }

    @Override
    public Optional<BookContent> findByLemmaId(long lemmaId) {
        return find("lemmaId", lemmaId);
    }

    @Override
    public boolean save(BookContent content) {
        try {
            if (content.getBookId() != null) {
                findByBookId(content.getBookId()).ifPresent(existing -> content.setId(existing.getId()));
            }
            if (content.getId() == null && content.getLemmaId() != null) {
                findByLemmaId(content.getLemmaId()).ifPresent(existing -> content.setId(existing.getId()));
            }
            template.save(content);
            return true;
        } catch (RuntimeException ex) {
            log.warn("写入图书内容失败: {}", ex.toString());
            return false;
        }
    }

    private Optional<BookContent> find(String field, long value) {
        try {
            BookContent found = template.findOne(Query.query(Criteria.where(field).is(value)), BookContent.class);
            return Optional.ofNullable(found);
        } catch (RuntimeException ex) {
            log.warn("读取图书内容失败 {}={}: {}", field, value, ex.toString());
            return Optional.empty();
        }
    }
}
