package com.bookmall.order.client;

import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * 调用目录服务查询图书、扣减库存和回补库存。
 * <p>
 * 连接和读取超时都是 3 秒。目录返回的业务错误码会转成本服务的 {@link BusinessException}，
 * 这样订单接口仍使用统一的错误结构。回补按 token 幂等，失败时向外抛出，不能假装库存已经加回。
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    /**
     * @param baseUrl      目录服务根地址，配置项 {@code bookmall.catalog-base-url}
     * @param objectMapper JSON 读写
     */
    public CatalogClient(@Value("${bookmall.catalog-base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.client = restClient(baseUrl, 3000);
        this.objectMapper = objectMapper;
    }

    /**
     * 查询一本图书的展示和库存快照。目录返回 40400 时视为图书不存在，不抛异常。
     *
     * @param bookId 图书主键
     * @return 存在时带价格、库存和上架状态；不存在时 {@code exists} 为 false
     */
    public BookSnapshot getBook(long bookId) {
        JsonNode body = exchange("GET", "/api/books/" + bookId, null);
        if (body.path("code").asInt() == 40400) {
            return BookSnapshot.missing(bookId);
        }
        if (body.path("code").asInt() != 0) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
        JsonNode data = body.path("data");
        return new BookSnapshot(
                bookId,
                true,
                data.path("title").asText(),
                data.path("coverUrl").isNull() ? null : data.path("coverUrl").asText(),
                new BigDecimal(data.path("price").asText()),
                data.path("stock").asInt(),
                data.path("status").asText());
    }

    /**
     * 请求目录服务扣减库存。库存不足和图书不存在分别映射为 40006、40400。
     *
     * @param bookId   图书主键
     * @param quantity 扣减数量
     * @param token    本轮扣减令牌，重复调用不会再次扣减
     */
    public void deduct(long bookId, int quantity, String token) {
        JsonNode body = exchange("POST", "/internal/books/" + bookId + "/stock/deduct",
                objectMapper.createObjectNode().put("quantity", quantity).put("token", token));
        int code = body.path("code").asInt(-1);
        if (code == 0) {
            return;
        }
        if (code == 40006) {
            throw new BusinessException(400, 40006, "库存不足", body.get("data"));
        }
        if (code == 40400) {
            throw BusinessException.notFound("图书不存在");
        }
        throw new BusinessException(500, 50000, "服务器内部错误");
    }

    /**
     * 请求目录服务按令牌回补库存。成功或目录确认无需回补时返回；否则重试后仍失败就抛出。
     *
     * @param bookId   图书主键
     * @param quantity 回补数量
     * @param token    扣减时使用的令牌
     */
    public void restore(long bookId, int quantity, String token) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                JsonNode body = exchange("POST", "/internal/books/" + bookId + "/stock/restore",
                        objectMapper.createObjectNode().put("quantity", quantity).put("token", token));
                int code = body.path("code").asInt(-1);
                if (code == 0) {
                    return;
                }
                if (code == 40400) {
                    throw BusinessException.notFound("图书不存在");
                }
                last = new BusinessException(500, 50000, "服务器内部错误");
            } catch (BusinessException ex) {
                if (ex.getCode() == 40400) {
                    throw ex;
                }
                last = ex;
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        log.error("回补库存失败 bookId={} quantity={} token={}", bookId, quantity, token, last);
        throw new BusinessException(500, 50000, "服务器内部错误");
    }

    /**
     * 订单取消时按数量还库存，不带扣减令牌。
     */
    public void restoreQuantity(long bookId, int quantity) {
        JsonNode body = exchange("POST", "/internal/books/" + bookId + "/stock/restore-qty",
                objectMapper.createObjectNode().put("quantity", quantity));
        int code = body.path("code").asInt(-1);
        if (code == 0) {
            return;
        }
        if (code == 40400) {
            throw BusinessException.notFound("图书不存在");
        }
        throw new BusinessException(500, 50000, "服务器内部错误");
    }

    /**
     * 发起一次 HTTP 调用并解析 JSON。HTTP 4xx/5xx 若带响应体，仍按业务响应解析。
     */
    private JsonNode exchange(String method, String path, JsonNode json) {
        try {
            RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
            RestClient.ResponseSpec response = json == null ? spec.retrieve() : spec.body(json).retrieve();
            String raw = response.body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            try {
                return objectMapper.readTree(ex.getResponseBodyAsString());
            } catch (Exception parse) {
                throw new BusinessException(500, 50000, "服务器内部错误");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, 50000, "服务器内部错误");
        }
    }

    /** 创建带连接和读取超时的客户端。 */
    private static RestClient restClient(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * 目录服务返回的图书快照，购物车和下单共用。
     *
     * @param bookId   图书主键
     * @param exists   图书是否存在
     * @param title    书名
     * @param coverUrl 封面地址
     * @param price    单价
     * @param stock    当前库存
     * @param status   {@code ON_SALE} 或 {@code OFF_SALE}
     */
    public record BookSnapshot(long bookId, boolean exists, String title, String coverUrl,
                               BigDecimal price, int stock, String status) {
        /** 目录返回 404 时使用的空快照。 */
        static BookSnapshot missing(long bookId) {
            return new BookSnapshot(bookId, false, null, null, null, 0, null);
        }
    }
}
