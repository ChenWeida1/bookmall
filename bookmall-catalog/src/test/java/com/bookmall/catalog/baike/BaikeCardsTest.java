package com.bookmall.catalog.baike;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 百科名片解析不访问网络。 */
class BaikeCardsTest {

    @Test
    void readsSynopsisWithoutPrice() {
        String json = """
                {"newLemmaId":2068378,"title":"围城","desc":"钱锺书著长篇讽刺小说","abstract":"一段梗概","url":"http://baike.baidu.com/item",
                 "card":[{"name":"作者","value":["钱锺书"]}]}
                """;
        BaikeCard card = BaikeCards.parse(json);
        assertEquals(2068378L, card.lemmaId());
        assertEquals("钱锺书", card.author());
        assertEquals("一段梗概", card.synopsis());
    }

    @Test
    void missingLemmaIsEmpty() {
        assertNull(BaikeCards.parse("{\"errno\":2}"));
    }
}
