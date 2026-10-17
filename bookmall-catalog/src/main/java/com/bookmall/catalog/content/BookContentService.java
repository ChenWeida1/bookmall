package com.bookmall.catalog.content;

import com.bookmall.catalog.baike.BaikeCard;
import com.bookmall.catalog.baike.BaikeClient;
import com.bookmall.catalog.entity.Book;
import com.bookmall.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 梗概等内容的读写，以及百科词条缓存。
 * <p>
 * 读失败时详情仍返回价格和库存。显式保存失败时返回内容库不可用。
 */
@Service
@RequiredArgsConstructor
public class BookContentService {

    private static final Logger log = LoggerFactory.getLogger(BookContentService.class);

    private final BookContentStore store;

    private final BaikeClient baike;

    /** 有文档时把内容字段放进详情。读失败只记日志。 */
    public void overlay(Map<String, Object> item, long bookId) {
        try {
            store.findByBookId(bookId).ifPresent(content -> copyFields(item, content));
        } catch (RuntimeException ex) {
            log.warn("读取图书内容失败 bookId={}: {}", bookId, ex.toString());
        }
    }

    /** 新增图书时可选写入梗概和标签。写失败不让图书创建失败。 */
    public void saveAdminExtras(long bookId, String synopsis, List<String> tags) {
        if ((synopsis == null || synopsis.isBlank()) && (tags == null || tags.isEmpty())) {
            return;
        }
        BookContent content = store.findByBookId(bookId).orElseGet(BookContent::new);
        content.setBookId(bookId);
        content.setSource(BookContent.ADMIN);
        if (synopsis != null && !synopsis.isBlank()) {
            content.setSynopsis(synopsis.trim());
        }
        if (tags != null && !tags.isEmpty()) {
            content.setTags(tags);
        }
        if (!store.save(content)) {
            log.warn("新增图书时内容未写入 bookId={}", bookId);
        }
    }

    /** 管理员改内容，来源记为 ADMIN。书必须已存在，由调用方检查。 */
    public void replaceAdmin(long bookId, String synopsis, String contents, String excerpt, List<String> tags, String authorBio) {
        BookContent content = store.findByBookId(bookId).orElseGet(BookContent::new);
        content.setBookId(bookId);
        content.setSource(BookContent.ADMIN);
        content.setSynopsis(blankToNull(synopsis));
        content.setContents(blankToNull(contents));
        content.setExcerpt(blankToNull(excerpt));
        content.setAuthorBio(blankToNull(authorBio));
        content.setTags(tags == null ? List.of() : tags);
        if (!store.save(content)) {
            throw new BusinessException(503, 50302, "内容库暂时不可用");
        }
    }

    /**
     * 按词查百科。没有词条时返回 null。命中后写入 Mongo，此时还没有 bookId。
     */
    public Map<String, Object> searchExternal(String keyword) {
        BaikeCard card = baike.lookup(keyword);
        if (card == null) {
            return null;
        }
        BookContent content = store.findByLemmaId(card.lemmaId()).orElseGet(BookContent::new);
        if (content.getBookId() == null) {
            mergeCard(content, card);
            if (!store.save(content)) {
                throw new BusinessException(503, 50302, "内容库暂时不可用");
            }
        }
        return externalView(content);
    }

    /** 引进前取出词条。同一词条已经有店内书时拒绝。 */
    public BaikeCard cardForImport(long lemmaId) {
        BookContent existing = store.findByLemmaId(lemmaId).orElse(null);
        if (existing != null && existing.getBookId() != null) {
            throw BusinessException.badRequest("已经引进");
        }
        if (existing != null && existing.getTitle() != null) {
            return new BaikeCard(lemmaId, existing.getTitle(), existing.getAuthor(), existing.getSynopsis(),
                    existing.getContents(), existing.getUrl(), null, null);
        }
        throw BusinessException.badRequest("请先搜索该词条");
    }

    /** 把已缓存的词条绑到新书。绑定失败时回滚新建的图书。 */
    public void bindImported(long bookId, BaikeCard card) {
        BookContent content = store.findByLemmaId(card.lemmaId()).orElseGet(BookContent::new);
        mergeCard(content, card);
        content.setBookId(bookId);
        if (!store.save(content)) {
            throw new BusinessException(503, 50302, "内容库暂时不可用");
        }
    }

    /**
     * 给尚未有正文的百科词条写入智谱梗概。已有管理员或百科正文时不覆盖。
     */
    public Map<String, Object> saveZhipuLemma(long lemmaId, String synopsis) {
        if (synopsis == null || synopsis.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        BookContent content = store.findByLemmaId(lemmaId).orElse(null);
        if (content == null) {
            throw BusinessException.notFound("图书不存在");
        }
        if (!content.lockedSynopsis()) {
            content.setSynopsis(synopsis.trim());
            content.setSource(BookContent.ZHIPU);
            if (!store.save(content)) {
                throw new BusinessException(503, 50302, "内容库暂时不可用");
            }
        }
        return externalView(content);
    }

    /** 客服读取内容。没有文档时字段为空。 */
    public Map<String, Object> viewForAssistant(Book book) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookId", book.getId());
        body.put("title", book.getTitle());
        body.put("author", book.getAuthor());
        body.put("description", book.getDescription());
        store.findByBookId(book.getId()).ifPresent(content -> copyFields(body, content));
        return body;
    }

    /**
     * 智谱补写。管理员或百科已经有梗概时保持原文。
     */
    public Map<String, Object> saveZhipu(Book book, String synopsis) {
        if (synopsis == null || synopsis.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        BookContent content = store.findByBookId(book.getId()).orElseGet(BookContent::new);
        if (content.lockedSynopsis()) {
            return viewForAssistant(book);
        }
        content.setBookId(book.getId());
        content.setSynopsis(synopsis.trim());
        content.setSource(BookContent.ZHIPU);
        if (!store.save(content)) {
            throw new BusinessException(503, 50302, "内容库暂时不可用");
        }
        return viewForAssistant(book);
    }

    /**
     * 用名片补齐词条。已有管理员或百科正文时不改梗概。名片没有摘要时保留已写入的智谱正文。
     */
    private static void mergeCard(BookContent content, BaikeCard card) {
        content.setLemmaId(card.lemmaId());
        content.setTitle(card.title());
        content.setAuthor(card.author());
        content.setUrl(card.url());
        if (card.contents() != null && !card.contents().isBlank()) {
            content.setContents(card.contents());
        }
        if (card.synopsis() != null && !card.synopsis().isBlank()
                && (!content.lockedSynopsis() || BookContent.BAIDU.equals(content.getSource()))) {
            content.setSynopsis(card.synopsis());
            content.setSource(BookContent.BAIDU);
        }
    }

    private static Map<String, Object> externalView(BookContent content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lemmaId", content.getLemmaId());
        body.put("title", content.getTitle());
        body.put("author", content.getAuthor());
        body.put("synopsis", content.getSynopsis());
        body.put("contents", content.getContents());
        body.put("url", content.getUrl());
        body.put("source", content.getSource());
        body.put("inStore", content.getBookId() != null);
        return body;
    }

    private static void copyFields(Map<String, Object> item, BookContent content) {
        item.put("synopsis", content.getSynopsis());
        item.put("contents", content.getContents());
        item.put("excerpt", content.getExcerpt());
        item.put("authorBio", content.getAuthorBio());
        item.put("source", content.getSource());
        item.put("tags", content.getTags() == null ? List.of() : new ArrayList<>(content.getTags()));
        if (content.getLemmaId() != null) {
            item.put("lemmaId", content.getLemmaId());
        }
        if (content.getUrl() != null) {
            item.put("url", content.getUrl());
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
