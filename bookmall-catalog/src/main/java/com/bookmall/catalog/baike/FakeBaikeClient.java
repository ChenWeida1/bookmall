package com.bookmall.catalog.baike;

import com.bookmall.common.error.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 测试替身。默认没有词条，单测可放入下一张名片或一次失败。
 */
@Component
@Profile("test")
public class FakeBaikeClient implements BaikeClient {

    private BaikeCard next;

    private boolean unavailable;

    public void setNext(BaikeCard next) {
        this.next = next;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public void reset() {
        next = null;
        unavailable = false;
    }

    @Override
    public BaikeCard lookup(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (unavailable) {
            throw new BusinessException(503, 50302, "外部书目暂时不可用");
        }
        BaikeCard card = next;
        next = null;
        return card;
    }
}
