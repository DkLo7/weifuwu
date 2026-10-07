package com.example.groupbuy.order;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import javax.annotation.PostConstruct;

@Service
public class OrderService {
    private final JdbcTemplate db;
    private final CatalogClient catalog;
    @Value("${app.jwt-secret}") private String jwtSecret;
    @Value("${app.internal-key}") private String internalKey;
    @Value("${app.order-timeout-minutes:15}") private int orderTimeoutMinutes;
    private static final String FIELDS = "order_no AS orderNo,user_id AS userId,client_key AS clientKey,product_id AS productId,product_name AS productName,quantity,unit_price_cents AS unitPriceCents,total_cents AS totalCents,pickup_point_id AS pickupPointId,pickup_point_name AS pickupPointName,status,pickup_code AS pickupCode,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') AS createdAt,DATE_FORMAT(expires_at,'%Y-%m-%d %H:%i:%s') AS expiresAt";

    /** 注入订单数据库和商品服务客户端。 */
    public OrderService(JdbcTemplate db, CatalogClient catalog) { this.db = db; this.catalog = catalog; }

    /** 为本地演示注册下单接口的每秒请求上限。 */
    @PostConstruct
    public void initializeFlowRule() {
        FlowRule rule = new FlowRule("createOrder");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(5);
        FlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    /** 创建订单并按订单号幂等预占库存。 */
    @SentinelResource(value="createOrder", blockHandler="createBlocked")
    public Map<String, Object> create(String authorization, OrderController.OrderInput input) {
        long userId = TokenUtil.userId(authorization, jwtSecret);
        if (!"USER".equals(TokenUtil.verify(authorization, jwtSecret).get("role"))) throw new ApiException(403, "仅普通用户可以下单");
        List<Map<String, Object>> existing = db.queryForList("SELECT order_no FROM orders WHERE user_id=? AND client_key=?", userId, input.clientKey);
        if (!existing.isEmpty()) return findOwned(userId, (String) existing.get(0).get("order_no"));
        if (input.quantity < 1 || input.quantity > 99) throw new ApiException(400, "购买数量须为 1 到 99");
        Map<String, Object> product;
        Map<String, Object> pickup;
        try {
            product = catalog.product(input.productId).data;
            pickup = catalog.pickupPoint(internalKey, input.pickupPointId).data;
        } catch (FeignException error) {
            throw new ApiException(error.status() == 404 ? 404 : 503, error.status() == 404 ? "商品或自提点不存在" : "商品服务暂时不可用");
        }
        long unitPrice = ((Number) product.get("priceCents")).longValue();
        String orderNo = UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO orders(order_no,user_id,client_key,product_id,product_name,quantity,unit_price_cents,total_cents,pickup_point_id,pickup_point_name,status,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,'CREATING',DATE_ADD(NOW(),INTERVAL ? MINUTE))", orderNo,userId,input.clientKey,input.productId,product.get("name"),input.quantity,unitPrice,unitPrice*input.quantity,input.pickupPointId,pickup.get("name"),orderTimeoutMinutes);
        } catch (DuplicateKeyException error) {
            return findByClientKey(userId, input.clientKey);
        }
        try {
            catalog.reserve(internalKey, orderNo, new CatalogClient.ReservationInput(input.productId, input.quantity));
            int changed = db.update("UPDATE orders SET status='UNPAID' WHERE order_no=? AND status='CREATING'", orderNo);
            if (changed != 1) throw new ApiException(409, "订单处理超时，请重新下单");
        } catch (Exception error) {
            db.update("UPDATE orders SET status='FAILED' WHERE order_no=? AND status='CREATING'", orderNo);
            releaseIfNeeded(orderNo, input.productId, input.quantity);
            if (error instanceof FeignException && ((FeignException) error).status() == 409) throw new ApiException(409, "商品已下架或库存不足");
            if (error instanceof ApiException) throw (ApiException) error;
            throw new ApiException(503, "下单暂时失败，请稍后重试");
        }
        return findOwned(userId, orderNo);
    }

    /** 将 Sentinel 限流转为前端可读的错误。 */
    public Map<String, Object> createBlocked(String authorization, OrderController.OrderInput input, BlockException error) {
        throw new ApiException(429, "下单过于频繁，请稍后再试");
    }

    /** 按当前用户和客户端请求号查找订单。 */
    private Map<String, Object> findByClientKey(long userId, String clientKey) {
        List<Map<String, Object>> rows = db.queryForList("SELECT " + FIELDS + " FROM orders WHERE user_id=? AND client_key=?", userId, clientKey);
        if (rows.isEmpty()) throw new ApiException(409, "订单正在创建，请刷新后查看");
        return rows.get(0);
    }

    /** 查询属于指定用户的订单。 */
    public Map<String, Object> findOwned(long userId, String orderNo) {
        List<Map<String, Object>> rows = db.queryForList("SELECT " + FIELDS + " FROM orders WHERE order_no=? AND user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new ApiException(404, "订单不存在");
        return rows.get(0);
    }

    /** 列出当前用户的订单。 */
    public List<Map<String, Object>> mine(String authorization) {
        return db.queryForList("SELECT " + FIELDS + " FROM orders WHERE user_id=? ORDER BY created_at DESC LIMIT 100", TokenUtil.userId(authorization, jwtSecret));
    }

    /** 模拟支付，仅允许未超时的待支付订单转为已支付。 */
    public Map<String, Object> pay(String authorization, String orderNo) {
        long userId = TokenUtil.userId(authorization, jwtSecret);
        String pickupCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        int changed = db.update("UPDATE orders SET status='PAID',pickup_code=? WHERE order_no=? AND user_id=? AND status='UNPAID' AND expires_at>NOW()", pickupCode,orderNo,userId);
        Map<String, Object> order = findOwned(userId, orderNo);
        if (changed == 0 && !"PAID".equals(order.get("status")) && !"COMPLETED".equals(order.get("status"))) throw new ApiException(409, "订单不能支付，可能已超时或取消");
        return order;
    }

    /** 用户取消待支付订单并触发库存释放。 */
    public Map<String, Object> cancel(String authorization, String orderNo) {
        long userId = TokenUtil.userId(authorization, jwtSecret);
        int changed = db.update("UPDATE orders SET status='CANCELED' WHERE order_no=? AND user_id=? AND status='UNPAID'", orderNo,userId);
        Map<String, Object> order = findOwned(userId, orderNo);
        if (changed == 0 && !"CANCELED".equals(order.get("status"))) throw new ApiException(409, "当前状态不能取消");
        releaseIfNeeded(orderNo, ((Number) order.get("productId")).longValue(), ((Number) order.get("quantity")).intValue());
        return findOwned(userId, orderNo);
    }

    /** 管理员查看全部订单。 */
    public List<Map<String, Object>> all(String authorization) {
        TokenUtil.requireAdmin(authorization, jwtSecret);
        return db.queryForList("SELECT " + FIELDS + " FROM orders ORDER BY created_at DESC LIMIT 200");
    }

    /** 管理员凭提货码将已支付订单核销。 */
    public Map<String, Object> pickup(String authorization, String pickupCode) {
        TokenUtil.requireAdmin(authorization, jwtSecret);
        String code = pickupCode.trim().toUpperCase();
        int changed = db.update("UPDATE orders SET status='COMPLETED' WHERE pickup_code=? AND status='PAID'", code);
        List<Map<String, Object>> rows = db.queryForList("SELECT " + FIELDS + " FROM orders WHERE pickup_code=?", code);
        if (rows.isEmpty()) throw new ApiException(404, "提货码不存在");
        if (changed == 0 && !"COMPLETED".equals(rows.get(0).get("status"))) throw new ApiException(409, "订单尚未支付或无法核销");
        return rows.get(0);
    }

    /** 释放库存并记录成功状态，失败留给定时任务重试。 */
    private void releaseIfNeeded(String orderNo, long productId, int quantity) {
        try {
            catalog.release(internalKey, orderNo, new CatalogClient.ReservationInput(productId, quantity));
            db.update("UPDATE orders SET stock_released=1 WHERE order_no=? AND status IN ('FAILED','CANCELED')", orderNo);
        } catch (Exception error) {
            // 库存释放接口幂等，定时任务会继续补偿。
        }
    }

    /** 定时取消超时订单并补偿失败或取消订单的库存。 */
    @Scheduled(fixedDelay=60000)
    public void reconcile() {
        db.update("UPDATE orders SET status='CANCELED' WHERE status='UNPAID' AND expires_at<=NOW()");
        db.update("UPDATE orders SET status='FAILED' WHERE status='CREATING' AND created_at<DATE_SUB(NOW(),INTERVAL 2 MINUTE)");
        List<Map<String, Object>> rows = db.queryForList("SELECT order_no,product_id,quantity FROM orders WHERE status IN ('FAILED','CANCELED') AND stock_released=0 LIMIT 100");
        for (Map<String, Object> row : rows) releaseIfNeeded((String) row.get("order_no"), ((Number) row.get("product_id")).longValue(), ((Number) row.get("quantity")).intValue());
    }
}
