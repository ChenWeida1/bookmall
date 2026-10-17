package com.bookmall.catalog.content;

import java.util.Optional;

/**
 * 图书内容的读写。实现必须在库不可用时让查询返回空，而不是抛给下单。
 */
public interface BookContentStore {

    /** 按店内图书查找。库不可用时为空。 */
    Optional<BookContent> findByBookId(long bookId);

    /** 按百科词条查找。库不可用时为空。 */
    Optional<BookContent> findByLemmaId(long lemmaId);

    /**
     * 按 bookId 或 lemmaId 更新已有文档，否则新增。
     *
     * @return 写入失败时为 false
     */
    boolean save(BookContent content);
}
