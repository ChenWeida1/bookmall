package com.bookmall.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bookmall.order.client.CatalogClient;
import com.bookmall.order.client.NotificationPublisher;
import com.bookmall.order.entity.CartItem;
import com.bookmall.order.mapper.CartItemMapper;
import com.bookmall.order.service.CartService;
import com.bookmall.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookmall.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单主流程测试。目录和通知用假客户端，避免依赖另外两个服务与 MQ。
 * 事务回滚会清掉用例里写入的购物车和订单。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderFlowTest {

    @Autowired
    OrderService orders;

    @Autowired
    CartItemMapper carts;

    @Autowired
    CartService cartService;

    private final ObjectMapper json = new ObjectMapper();

    @MockBean
    CatalogClient catalog;

    @MockBean
    NotificationPublisher notifications;

    /** 空购物车不能下单，错误码 40005。 */
    @Test
    void emptyCartFails() {
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.create(910001L, null));
        assertEquals(40005, ex.getCode());
    }

    /** 库存不够时在扣减之前失败，目录服务不应收到扣减请求。 */
    @Test
    void insufficientStockDoesNotDeduct() {
        saveCart(910002L, 1L, 2);
        when(catalog.getBook(1L)).thenReturn(book(1, 1));
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.create(910002L, null));
        assertEquals(40006, ex.getCode());
        verify(catalog, never()).deduct(anyLong(), anyInt(), anyString());
    }

    /** 下单成功后购物车被清空，并投递下单成功通知。两本 39.80 合计 79.60。 */
    @Test
    void successClearsCartAndNotifies() {
        saveCart(910003L, 1L, 2);
        when(catalog.getBook(1L)).thenReturn(book(1, 12));
        var created = orders.create(910003L, null);
        assertEquals("CREATED", created.get("status"));
        assertTrue(carts.selectList(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, 910003L)).isEmpty());
        verify(notifications).orderCreated(eq(910003L), anyString(), eq("79.60"));
    }

    /** 第二本书扣减失败时，已经扣掉的第一本书要被回补。 */
    @Test
    void deductFailureRestoresEarlierBooks() {
        saveCart(910004L, 1L, 1);
        saveCart(910004L, 2L, 1);
        when(catalog.getBook(1L)).thenReturn(book(1, 5));
        when(catalog.getBook(2L)).thenReturn(book(2, 5));
        org.mockito.Mockito.doThrow(new BusinessException(400, 40006, "库存不足"))
                .when(catalog).deduct(eq(2L), eq(1), anyString());
        assertThrows(BusinessException.class, () -> orders.create(910004L, null));
        verify(catalog).restore(eq(1L), eq(1), anyString());
        verify(catalog).restore(eq(2L), eq(1), anyString());
    }

    /** 扣减时才发现库存不足，响应仍是 data.items，并且已经扣掉的库存要回补。 */
    @Test
    void deductRaceStillReportsItems() {
        saveCart(910030L, 1L, 2);
        when(catalog.getBook(1L)).thenReturn(book(1, 5));
        var data = json.createObjectNode().put("bookId", 1).put("stock", 1).put("quantity", 2);
        org.mockito.Mockito.doThrow(new BusinessException(400, 40006, "库存不足", data))
                .when(catalog).deduct(eq(1L), eq(2), anyString());
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.create(910030L, null));
        assertEquals(40006, ex.getCode());
        var items = ((java.util.Map<?, ?>) ex.getData()).get("items");
        var row = (java.util.Map<?, ?>) ((java.util.List<?>) items).get(0);
        assertEquals(1L, ((Number) row.get("bookId")).longValue());
        assertEquals("围城", row.get("bookTitle"));
        assertEquals(1, ((Number) row.get("stock")).intValue());
        assertEquals(2, ((Number) row.get("quantity")).intValue());
        verify(catalog).restore(eq(1L), eq(2), anyString());
    }

    /** 取消成功：库存按数量加回，并投递取消通知。 */
    @Test
    void cancelRestoresAndNotifies() {
        saveCart(910031L, 1L, 1);
        when(catalog.getBook(1L)).thenReturn(book(1, 5));
        var created = orders.create(910031L, null);
        long id = ((Number) created.get("id")).longValue();
        var cancelled = orders.cancel(910031L, id);
        assertEquals("CANCELLED", cancelled.get("status"));
        verify(catalog).restoreQuantity(1L, 1);
        verify(notifications).orderCancelled(eq(910031L), anyString());
    }

    /** 第二本还库存失败时，第一本已加回的数量要扣回去，订单仍是已创建。 */
    @Test
    void cancelFailureUndoesRestoredStock() {
        saveCart(910032L, 1L, 1);
        saveCart(910032L, 2L, 1);
        when(catalog.getBook(1L)).thenReturn(book(1, 5));
        when(catalog.getBook(2L)).thenReturn(book(2, 5));
        var created = orders.create(910032L, null);
        long id = ((Number) created.get("id")).longValue();
        org.mockito.Mockito.doThrow(new RuntimeException("catalog down"))
                .when(catalog).restoreQuantity(eq(2L), eq(1));
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.cancel(910032L, id));
        assertEquals(50000, ex.getCode());
        verify(catalog).deduct(eq(1L), eq(1), startsWith("cancel-undo-"));
        assertEquals("CREATED", orders.detail(910032L, id).get("status"));
        verify(notifications, never()).orderCancelled(anyLong(), anyString());
    }

    /** 已加回的库存扣不回去时，订单保持已取消，避免再次取消把库存再加一次。 */
    @Test
    void cancelStaysCancelledWhenUndoFails() {
        saveCart(910033L, 1L, 1);
        saveCart(910033L, 2L, 1);
        when(catalog.getBook(1L)).thenReturn(book(1, 5));
        when(catalog.getBook(2L)).thenReturn(book(2, 5));
        org.mockito.Mockito.doAnswer(invocation -> {
            String token = invocation.getArgument(2, String.class);
            if (token.startsWith("cancel-undo-")) {
                throw new RuntimeException("undo failed");
            }
            return null;
        }).when(catalog).deduct(anyLong(), anyInt(), anyString());
        var created = orders.create(910033L, null);
        long id = ((Number) created.get("id")).longValue();
        org.mockito.Mockito.doThrow(new RuntimeException("catalog down"))
                .when(catalog).restoreQuantity(eq(2L), eq(1));
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.cancel(910033L, id));
        assertEquals(50000, ex.getCode());
        assertEquals("CANCELLED", orders.detail(910033L, id).get("status"));
        verify(notifications, never()).orderCancelled(anyLong(), anyString());
    }

    /** 同一本书连续加入时合并数量。第二次会超过库存，这一次累加要撤销，购物车仍是 2 本。 */
    @Test
    void repeatedAddMergesUntilStock() throws Exception {
        when(catalog.getBook(1L)).thenReturn(book(1, 3));
        cartService.add(910010L, json.readTree("{\"bookId\":1,\"quantity\":2}"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> cartService.add(910010L, json.readTree("{\"bookId\":1,\"quantity\":2}")));
        assertEquals(40006, ex.getCode());
        CartItem item = carts.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, 910010L)
                .eq(CartItem::getBookId, 1L));
        assertEquals(2, item.getQuantity());
    }

    /** 写入一条测试用购物车记录。 */
    private void saveCart(long userId, long bookId, int quantity) {
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setBookId(bookId);
        item.setQuantity(quantity);
        carts.insert(item);
    }

    /** 构造一本在售、单价 39.80 的图书快照。 */
    private static CatalogClient.BookSnapshot book(long id, int stock) {
        return new CatalogClient.BookSnapshot(id, true, "围城", null, new BigDecimal("39.80"), stock, "ON_SALE");
    }
}
