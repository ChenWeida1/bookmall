package com.bookmall.catalog.content;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getLemmaId() {
        return lemmaId;
    }

    public void setLemmaId(Long lemmaId) {
        this.lemmaId = lemmaId;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public String getContents() {
        return contents;
    }

    public void setContents(String contents) {
        this.contents = contents;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getAuthorBio() {
        return authorBio;
    }

    public void setAuthorBio(String authorBio) {
        this.authorBio = authorBio;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /** 已有管理员或百科正文时，智谱不能改写。 */
    public boolean lockedSynopsis() {
        if (synopsis == null || synopsis.isBlank()) {
            return false;
        }
        return ADMIN.equals(source) || BAIDU.equals(source);
    }
}
