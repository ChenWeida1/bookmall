package com.bookmall.catalog.baike;

/**
 * 百科名片里和引进图书有关的字段。没有价格和库存。
 */
public record BaikeCard(
        long lemmaId,
        String title,
        String author,
        String synopsis,
        String contents,
        String url,
        String coverUrl,
        String description) {
}
