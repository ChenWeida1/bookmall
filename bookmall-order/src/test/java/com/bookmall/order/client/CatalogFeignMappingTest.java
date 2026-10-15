package com.bookmall.order.client;

import com.bookmall.common.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feign 把目录服务的 40006 / 40400 映射成和原来 RestClient 一样的业务异常。
 */
class CatalogFeignMappingTest {

    private final CatalogFeign feign = mock(CatalogFeign.class);
    private final CatalogClient client = new CatalogClient(feign, new ObjectMapper());

    @Test
    void shortageKeepsData() {
        when(feign.deduct(1L, new CatalogFeign.StockBody(2, "t1")))
                .thenThrow(error(400, "{\"code\":40006,\"message\":\"库存不足\",\"data\":{\"bookId\":1,\"stock\":0}}"));
        BusinessException ex = assertThrows(BusinessException.class, () -> client.deduct(1L, 2, "t1"));
        assertEquals(40006, ex.getCode());
        assertEquals(0, ((JsonNode) ex.getData()).path("stock").asInt());
    }

    @Test
    void missingBookIs40400() {
        when(feign.book(9L)).thenThrow(error(404, "{\"code\":40400,\"message\":\"图书不存在\",\"data\":null}"));
        CatalogClient.BookSnapshot snapshot = client.getBook(9L);
        assertFalse(snapshot.exists());
        assertEquals(9L, snapshot.bookId());
    }

    private static FeignException error(int status, String json) {
        Request request = Request.create(Request.HttpMethod.POST, "http://catalog/x", Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .body(json, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("catalog", response);
    }
}
