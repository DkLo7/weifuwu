package com.example.groupbuy.catalog;

import com.example.groupbuy.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class InventoryService {
    private final JdbcTemplate db;

    /** 注入商品数据库。 */
    public InventoryService(JdbcTemplate db) { this.db = db; }

    /** 幂等预占库存；每个订单号只允许对应一项商品与数量。 */
    @Transactional
    public void reserve(String orderNo, long productId, int quantity) {
        if (quantity < 1) throw new ApiException(400, "数量不正确");
        db.update("INSERT IGNORE INTO stock_reservations(order_no,product_id,quantity,status) VALUES (?,?,?,'NEW')", orderNo, productId, quantity);
        Map<String, Object> row = db.queryForMap("SELECT product_id,quantity,status FROM stock_reservations WHERE order_no=? FOR UPDATE", orderNo);
        if (((Number) row.get("product_id")).longValue() != productId || ((Number) row.get("quantity")).intValue() != quantity) throw new ApiException(409, "订单库存请求不一致");
        String status = (String) row.get("status");
        if ("RESERVED".equals(status)) return;
        if ("RELEASED".equals(status)) throw new ApiException(409, "订单库存已释放");
        int changed = db.update("UPDATE products SET available_stock=available_stock-? WHERE id=? AND enabled=1 AND available_stock>=?", quantity, productId, quantity);
        if (changed != 1) throw new ApiException(409, "商品已下架或库存不足");
        db.update("UPDATE stock_reservations SET status='RESERVED' WHERE order_no=?", orderNo);
    }

    /** 幂等释放库存；无预占时写入墓碑以阻止迟到的预占请求。 */
    @Transactional
    public void release(String orderNo, long productId, int quantity) {
        if (quantity < 1) throw new ApiException(400, "数量不正确");
        db.update("INSERT IGNORE INTO stock_reservations(order_no,product_id,quantity,status) VALUES (?,?,?,'RELEASED')", orderNo, productId, quantity);
        Map<String, Object> row = db.queryForMap("SELECT product_id,quantity,status FROM stock_reservations WHERE order_no=? FOR UPDATE", orderNo);
        if (((Number) row.get("product_id")).longValue() != productId || ((Number) row.get("quantity")).intValue() != quantity) throw new ApiException(409, "订单库存请求不一致");
        if ("RESERVED".equals(row.get("status"))) {
            db.update("UPDATE products SET available_stock=available_stock+? WHERE id=?", quantity, productId);
            db.update("UPDATE stock_reservations SET status='RELEASED' WHERE order_no=?", orderNo);
        }
    }
}
