package com.bookmall.order;

import com.bookmall.common.error.BusinessException;
import com.bookmall.order.client.CatalogClient;
import com.bookmall.order.client.NotificationPublisher;
import com.bookmall.order.entity.CartItem;
import com.bookmall.order.mapper.CartItemMapper;
import com.bookmall.order.mq.PayTimeoutPublisher;
import com.bookmall.order.service.OrderService;
import com.bookmall.order.service.OrderWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 写订单失败时按令牌回补。回补本身失败则对外是 50000，不再保留原来的写入异常。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCreateFailureTest {

    @Autowired
    OrderService orders;

    @Autowired
    CartItemMapper carts;

    @MockBean
    CatalogClient catalog;

    @MockBean
    NotificationPublisher notifications;

    @MockBean
    PayTimeoutPublisher payTimeouts;

    @MockBean
    OrderWriter writer;

    @Test
    void saveFailureRestoresDeductedStock() {
        CartItem item = new CartItem();
        item.setUserId(910060L);
        item.setBookId(1L);
        item.setQuantity(1);
        carts.insert(item);
        when(catalog.getBook(1L)).thenReturn(
                new CatalogClient.BookSnapshot(1L, true, "围城", null, new BigDecimal("39.80"), 5, "ON_SALE"));
        when(writer.save(anyLong(), anyString(), any(), anyList())).thenThrow(new RuntimeException("write failed"));
        assertThrows(RuntimeException.class, () -> orders.create(910060L, null));
        verify(catalog).deduct(anyLong(), anyInt(), anyString());
        verify(catalog).restore(eq(1L), eq(1), anyString());
        verify(notifications, never()).orderCreated(anyLong(), anyString(), anyString());
    }

    /** 还库存中途失败时，对外只能是 50000，占用令牌留给下次下单继续回补。 */
    @Test
    void restoreFailureDuringCompensateIsInternalError() {
        CartItem item = new CartItem();
        item.setUserId(910061L);
        item.setBookId(1L);
        item.setQuantity(1);
        carts.insert(item);
        when(catalog.getBook(1L)).thenReturn(
                new CatalogClient.BookSnapshot(1L, true, "围城", null, new BigDecimal("39.80"), 5, "ON_SALE"));
        when(writer.save(anyLong(), anyString(), any(), anyList())).thenThrow(new RuntimeException("write failed"));
        org.mockito.Mockito.doThrow(new RuntimeException("restore failed"))
                .when(catalog).restore(anyLong(), anyInt(), anyString());
        BusinessException ex = assertThrows(BusinessException.class, () -> orders.create(910061L, null));
        assertEquals(50000, ex.getCode());
        verify(notifications, never()).orderCreated(anyLong(), anyString(), anyString());
    }
}
