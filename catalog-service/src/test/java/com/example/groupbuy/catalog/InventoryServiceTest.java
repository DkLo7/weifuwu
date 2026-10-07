package com.example.groupbuy.catalog;

import com.example.groupbuy.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    private JdbcTemplate db;
    private InventoryService inventory;

    /** 创建隔离的库存服务。 */
    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        inventory = new InventoryService(db);
    }

    /** 同一订单重复预占不会再次扣减库存。 */
    @Test
    void duplicateReserveDoesNotDecrementAgain() {
        when(db.queryForMap(anyString(), eq("order-1"))).thenReturn(row("RESERVED"));
        inventory.reserve("order-1", 4, 2);
        verify(db, never()).update(startsWith("UPDATE products"), any(), any(), any());
    }

    /** 库存不足时返回明确冲突。 */
    @Test
    void insufficientStockIsRejected() {
        when(db.queryForMap(anyString(), eq("order-1"))).thenReturn(row("NEW"));
        when(db.update(startsWith("UPDATE products"), any(), any(), any())).thenReturn(0);
        assertEquals(409, assertThrows(ApiException.class, () -> inventory.reserve("order-1", 4, 2)).status);
    }

    /** 已释放的订单再次释放不会增加库存。 */
    @Test
    void duplicateReleaseDoesNotIncrementAgain() {
        when(db.queryForMap(anyString(), eq("order-1"))).thenReturn(row("RELEASED"));
        inventory.release("order-1", 4, 2);
        verify(db, never()).update(startsWith("UPDATE products"), any(), any());
    }

    /** 正常释放只增加一次库存。 */
    @Test
    void releaseReturnsStock() {
        when(db.queryForMap(anyString(), eq("order-1"))).thenReturn(row("RESERVED"));
        inventory.release("order-1", 4, 2);
        verify(db).update(startsWith("UPDATE products"), eq(2), eq(4L));
    }

    /** 构造库存预占记录。 */
    private Map<String, Object> row(String status) {
        Map<String, Object> result = new HashMap<>();
        result.put("product_id", 4L); result.put("quantity", 2); result.put("status", status);
        return result;
    }
}
