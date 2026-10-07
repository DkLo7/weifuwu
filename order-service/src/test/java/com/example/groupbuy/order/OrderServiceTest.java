package com.example.groupbuy.order;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private JdbcTemplate db;
    private CatalogClient catalog;
    private OrderService service;
    private String userToken;
    private String adminToken;

    /** 创建隔离的订单业务与下游客户端。 */
    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        catalog = mock(CatalogClient.class);
        service = new OrderService(db, catalog);
        ReflectionTestUtils.setField(service, "jwtSecret", "unit-test-secret");
        ReflectionTestUtils.setField(service, "internalKey", "internal-test-key");
        userToken = "Bearer " + TokenUtil.issue(7, "USER", "unit-test-secret");
        adminToken = "Bearer " + TokenUtil.issue(1, "ADMIN", "unit-test-secret");
    }

    /** 重复客户端请求号直接返回原订单，不重复预占库存。 */
    @Test
    void duplicateCreateReturnsExistingOrder() {
        Map<String, Object> existing = new HashMap<>(); existing.put("order_no", "order-1");
        when(db.queryForList(startsWith("SELECT order_no"), eq(7L), eq("request-1"))).thenReturn(Collections.singletonList(existing));
        when(db.queryForList(startsWith("SELECT order_no AS"), eq("order-1"), eq(7L))).thenReturn(Collections.singletonList(order("UNPAID")));
        OrderController.OrderInput input = new OrderController.OrderInput(); input.clientKey = "request-1";
        assertEquals("order-1", service.create(userToken, input).get("orderNo"));
        verifyNoInteractions(catalog);
    }

    /** 成功下单时向商品服务预占一次库存。 */
    @Test
    void createReservesStock() {
        when(db.queryForList(startsWith("SELECT order_no FROM"), eq(7L), eq("request-2"))).thenReturn(Collections.emptyList());
        Map<String, Object> product = new HashMap<>(); product.put("name", "鲜蔬组合"); product.put("priceCents", 2990);
        Map<String, Object> point = new HashMap<>(); point.put("name", "社区服务中心");
        when(catalog.product(4L)).thenReturn(ApiResponse.ok(product));
        when(catalog.pickupPoint("internal-test-key", 1L)).thenReturn(ApiResponse.ok(point));
        when(db.update(startsWith("UPDATE orders SET status='UNPAID'"), anyString())).thenReturn(1);
        when(db.queryForList(startsWith("SELECT order_no AS"), anyString(), eq(7L))).thenReturn(Collections.singletonList(order("UNPAID")));
        OrderController.OrderInput input = new OrderController.OrderInput();
        input.clientKey = "request-2"; input.productId = 4; input.pickupPointId = 1; input.quantity = 2;
        assertEquals("UNPAID", service.create(userToken, input).get("status"));
        verify(catalog).reserve(eq("internal-test-key"), anyString(), any(CatalogClient.ReservationInput.class));
    }

    /** 已支付订单不能取消，也不会释放库存。 */
    @Test
    void paidOrderCannotBeCanceled() {
        when(db.queryForList(startsWith("SELECT order_no AS"), eq("order-1"), eq(7L))).thenReturn(Collections.singletonList(order("PAID")));
        assertEquals(409, assertThrows(ApiException.class, () -> service.cancel(userToken, "order-1")).status);
        verifyNoInteractions(catalog);
    }

    /** 未支付订单取消后释放库存。 */
    @Test
    void cancelReleasesStock() {
        when(db.update(startsWith("UPDATE orders SET status='CANCELED'"), eq("order-1"), eq(7L))).thenReturn(1);
        when(db.queryForList(startsWith("SELECT order_no AS"), eq("order-1"), eq(7L))).thenReturn(Collections.singletonList(order("CANCELED")));
        service.cancel(userToken, "order-1");
        verify(catalog).release(eq("internal-test-key"), eq("order-1"), any(CatalogClient.ReservationInput.class));
    }

    /** 重复支付返回原提货码。 */
    @Test
    void repeatedPaymentKeepsPickupCode() {
        Map<String, Object> paid = order("PAID"); paid.put("pickupCode", "CODE123");
        when(db.queryForList(startsWith("SELECT order_no AS"), eq("order-1"), eq(7L))).thenReturn(Collections.singletonList(paid));
        assertEquals("CODE123", service.pay(userToken, "order-1").get("pickupCode"));
    }

    /** 管理员可以核销已支付提货码。 */
    @Test
    void pickupCompletesPaidOrder() {
        when(db.update(startsWith("UPDATE orders SET status='COMPLETED'"), eq("CODE123"))).thenReturn(1);
        when(db.queryForList(startsWith("SELECT order_no AS"), eq("CODE123"))).thenReturn(Collections.singletonList(order("COMPLETED")));
        assertEquals("COMPLETED", service.pickup(adminToken, "code123").get("status"));
    }

    /** 定时任务重试未释放库存的失败订单。 */
    @Test
    void reconcileRetriesRelease() {
        Map<String, Object> row = new HashMap<>();
        row.put("order_no", "order-1"); row.put("product_id", 4L); row.put("quantity", 2);
        when(db.queryForList(startsWith("SELECT order_no,product_id,quantity"))).thenReturn(Collections.singletonList(row));
        service.reconcile();
        verify(db).update(startsWith("UPDATE orders SET status='CANCELED'"));
        verify(catalog).release(eq("internal-test-key"), eq("order-1"), any(CatalogClient.ReservationInput.class));
    }

    /** 构造订单查询结果。 */
    private Map<String, Object> order(String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("orderNo", "order-1"); row.put("status", status);
        row.put("productId", 4L); row.put("quantity", 2);
        return row;
    }
}
