package com.bookmall.catalog.service;

import com.bookmall.catalog.baike.BaikeCard;
import com.bookmall.catalog.baike.BaikeClient;
import com.bookmall.catalog.config.CompareProperties;
import com.bookmall.catalog.content.BookContent;
import com.bookmall.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 管理员进货对照。每本书名一个 JDK 21 虚拟线程，同时查本店和百度百科。
 * <p>
 * 虚拟线程只包住这次阻塞查询。下单扣库存仍在请求线程里按令牌回补，不改到这里。
 * 同时进行的查询有上限，避免占满数据库连接。某一本百科连不上时，只把这一行标为不可用。
 */
@Service
@RequiredArgsConstructor
public class BookCompareService {

    private final CatalogService catalog;

    private final BaikeClient baike;

    private final CompareProperties limits;

    /**
     * 按提交顺序返回对照结果。空白书名略过，重复书名只查一次。
     *
     * @param raw JSON 里的 titles
     * @return items
     */
    public Map<String, Object> compare(Object raw) {
        List<String> queries = normalize(raw);
        Semaphore limit = new Semaphore(Math.max(1, limits.getParallelism()));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Map<String, Object>>> futures = queries.stream()
                    .map(query -> executor.submit(() -> one(query, limit)))
                    .toList();
            long deadline = System.nanoTime() + limits.getDeadline().toNanos();
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                items.add(await(futures.get(i), queries.get(i), deadline));
            }
            return Map.of("items", items);
        }
    }

    private Map<String, Object> one(String query, Semaphore limit) {
        boolean acquired = false;
        try {
            acquired = limit.tryAcquire(limits.getPermitWait().toNanos(), TimeUnit.NANOSECONDS);
            if (!acquired) {
                return unavailable(query);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("query", query);
            row.put("stores", stores(query));
            External external = external(query);
            row.put("external", external.card());
            row.put("externalStatus", external.status());
            return row;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unavailable(query);
        } finally {
            if (acquired) {
                limit.release();
            }
        }
    }

    /** 本店命中，含下架书。只留对照需要的字段。 */
    private List<Map<String, Object>> stores(String query) {
        List<Map<String, Object>> found = catalog.searchForAssistant(query, null, null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> book : found) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", book.get("id"));
            row.put("title", book.get("title"));
            row.put("author", book.get("author"));
            row.put("price", book.get("price"));
            row.put("stock", book.get("stock"));
            row.put("status", book.get("status"));
            rows.add(row);
        }
        return rows;
    }

    /** 百科名片。连不上时这一行不可用，不让整批失败。 */
    private External external(String query) {
        try {
            BaikeCard card = baike.lookup(query);
            if (card == null) {
                return new External(null, "EMPTY");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("lemmaId", card.lemmaId());
            body.put("title", card.title());
            body.put("author", card.author());
            body.put("synopsis", card.synopsis());
            body.put("url", card.url());
            body.put("source", BookContent.BAIDU);
            return new External(body, "OK");
        } catch (BusinessException ex) {
            if (ex.getCode() == 50302) {
                return new External(null, "UNAVAILABLE");
            }
            throw ex;
        }
    }

    private Map<String, Object> await(Future<Map<String, Object>> future, String query, long deadline) {
        long wait = deadline - System.nanoTime();
        try {
            if (wait <= 0) {
                future.cancel(true);
                return unavailable(query);
            }
            return future.get(wait, TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return unavailable(query);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new BusinessException(500, 50000, "服务器内部错误");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BusinessException business) {
                throw business;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
    }

    private static Map<String, Object> unavailable(String query) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("query", query);
        row.put("stores", List.of());
        row.put("external", null);
        row.put("externalStatus", "UNAVAILABLE");
        return row;
    }

    private List<String> normalize(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw BusinessException.badRequest("参数不合法");
        }
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > 128) {
                throw BusinessException.badRequest("参数不合法");
            }
            queries.add(text);
        }
        if (queries.isEmpty() || queries.size() > limits.getMaxTitles()) {
            throw BusinessException.badRequest("参数不合法");
        }
        return List.copyOf(queries);
    }

    private record External(Map<String, Object> card, String status) {
    }
}
