package com.bookmall.assistant;

import com.bookmall.assistant.client.CatalogQueryClient;
import com.bookmall.assistant.tool.BookTools;
import com.bookmall.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服工具只查图书，路径里不出现订单或购物车。
 */
class AssistantToolBoundaryTest {

    @Test
    void catalogClientDoesNotTouchOrders() {
        for (Method method : CatalogQueryClient.class.getDeclaredMethods()) {
            String path = pathOf(method);
            assertFalse(path.contains("orders"), path);
            assertFalse(path.contains("cart"), path);
            assertFalse(path.contains("pay"), path);
        }
    }

    @Test
    void searchGoesToCatalog() {
        CatalogQueryClient client = mock(CatalogQueryClient.class);
        when(client.search("围城", null, null)).thenReturn(ApiResponse.ok(List.of()));
        BookTools tools = new BookTools(client, new ObjectMapper());
        String result = tools.searchBooks("围城", null, null);
        verify(client).search("围城", null, null);
        assertTrue(result.contains("没有查到"));
    }

    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null && get.value().length > 0) {
            return get.value()[0];
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null && post.value().length > 0) {
            return post.value()[0];
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            return mapping.value()[0];
        }
        return "";
    }
}
