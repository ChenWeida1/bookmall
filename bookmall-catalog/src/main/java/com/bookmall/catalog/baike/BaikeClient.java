package com.bookmall.catalog.baike;

/**
 * 按词查询一条百科名片。没有词条时返回空，连不上时抛业务异常。
 */
public interface BaikeClient {

    /**
     * @param keyword 书名或作者，不能为空白
     * @return 没有词条时为 null
     */
    BaikeCard lookup(String keyword);
}
