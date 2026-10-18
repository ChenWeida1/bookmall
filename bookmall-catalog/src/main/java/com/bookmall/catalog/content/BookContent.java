package com.bookmall.catalog.content;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 一本书的内容文档。价格、库存和上下架不放在这里。
 * <p>
 * {@code bookId} 有值时唯一，表示已上架到 PostgreSQL。
 * {@code lemmaId} 是百度百科词条编号，未引进时 {@code bookId} 为空。
 */
@Getter
@Setter
@Document("book_content")
public class BookContent {

    /** 管理员手写。智谱不能覆盖。 */
    public static final String ADMIN = "ADMIN";

    /** 百科摘要。智谱不能覆盖已有正文。 */
    public static final String BAIDU = "BAIDU";

    /** 智谱补写。 */
    public static final String ZHIPU = "ZHIPU";

    @Id
    private String id;

    /** 店内图书主键。未引进的百科词条为空。 */
    @Indexed(sparse = true, unique = true)
    private Long bookId;

    /** 百科词条编号。 */
    @Indexed(sparse = true, unique = true)
    private Long lemmaId;

    /** 梗概。 */
    private String synopsis;

    /** 目录，多行文本。 */
    private String contents;

    /** 摘录。 */
    private String excerpt;

    /** 标签。 */
    private List<String> tags = new ArrayList<>();

    /** 作者简介。 */
    private String authorBio;

    /** 与图书表分开保存的 ISBN，可空。 */
    private String isbn;

    /** {@link #ADMIN}、{@link #BAIDU} 或 {@link #ZHIPU}。 */
    private String source;

    /** 尚未引进时用来展示的书名。 */
    private String title;

    /** 尚未引进时用来展示的作者。 */
    private String author;

    /** 百科链接。 */
    private String url;

    /** 已有管理员或百科正文时，智谱不能改写。 */
    public boolean lockedSynopsis() {
        if (synopsis == null || synopsis.isBlank()) {
            return false;
        }
        return ADMIN.equals(source) || BAIDU.equals(source);
    }
}
