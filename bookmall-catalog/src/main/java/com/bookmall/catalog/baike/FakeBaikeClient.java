package com.bookmall.catalog.baike;

import com.bookmall.common.error.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 测试替身。默认没有词条，单测可放入下一张名片或一次失败。
 * 进货对照可按书名分别放入名片，并记录查询是否跑在虚拟线程上。
 */
@Component
@Profile("test")
public class FakeBaikeClient implements BaikeClient {

    private final ConcurrentHashMap<String, Optional<BaikeCard>> scripted = new ConcurrentHashMap<>();

    private final Set<String> down = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean sawVirtual = new AtomicBoolean();

    private BaikeCard next;

    private boolean unavailable;

    public void setNext(BaikeCard next) {
        this.next = next;
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    /** 按书名返回固定名片。传入 null 表示这本书没有词条。 */
    public void script(String keyword, BaikeCard card) {
        scripted.put(keyword, Optional.ofNullable(card));
    }

    /** 这本书名的百科调用按连不上处理。 */
    public void scriptUnavailable(String keyword) {
        down.add(keyword);
    }

    /** 是否有一次查询发生在虚拟线程上。 */
    public boolean sawVirtualThread() {
        return sawVirtual.get();
    }

    public void reset() {
        next = null;
        unavailable = false;
        scripted.clear();
        down.clear();
        sawVirtual.set(false);
    }

    @Override
    public BaikeCard lookup(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw BusinessException.badRequest("参数不合法");
        }
        if (Thread.currentThread().isVirtual()) {
            sawVirtual.set(true);
        }
        if (unavailable || down.contains(keyword)) {
            throw new BusinessException(503, 50302, "外部书目暂时不可用");
        }
        Optional<BaikeCard> script = scripted.get(keyword);
        if (script != null) {
            return script.orElse(null);
        }
        BaikeCard card = next;
        next = null;
        return card;
    }
}
