package com.example.groupbuy.catalog;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
public class CatalogController {
    private final JdbcTemplate db;
    private final InventoryService inventory;
    @Value("${app.jwt-secret}") private String jwtSecret;
    @Value("${app.internal-key}") private String internalKey;

    /** 注入数据库和库存业务。 */
    public CatalogController(JdbcTemplate db, InventoryService inventory) { this.db = db; this.inventory = inventory; }

    /** 列出已上架且仍可购买的商品。 */
    @GetMapping("/api/products")
    public ApiResponse<List<Map<String, Object>>> products() {
        return ApiResponse.ok(db.queryForList("SELECT id,name,description,image_url AS imageUrl,price_cents AS priceCents,available_stock AS availableStock FROM products WHERE enabled=1 ORDER BY id DESC"));
    }

    /** 读取商品快照，供用户浏览及订单服务查询。 */
    @GetMapping("/api/products/{id}")
    public ApiResponse<Map<String, Object>> product(@PathVariable long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT id,name,description,image_url AS imageUrl,price_cents AS priceCents,available_stock AS availableStock,enabled FROM products WHERE id=?", id);
        if (rows.isEmpty() || !((Boolean) rows.get(0).get("enabled"))) throw new ApiException(404, "商品不存在或已下架");
        return ApiResponse.ok(rows.get(0));
    }

    /** 列出所有可用自提点。 */
    @GetMapping("/api/pickup-points")
    public ApiResponse<List<Map<String, Object>>> pickupPoints() {
        return ApiResponse.ok(db.queryForList("SELECT id,name,address FROM pickup_points WHERE enabled=1 ORDER BY id"));
    }

    /** 查询自提点供订单服务校验。 */
    @GetMapping("/internal/pickup-points/{id}")
    public ApiResponse<Map<String, Object>> pickupPoint(@RequestHeader("X-Internal-Key") String key, @PathVariable long id) {
        requireInternal(key);
        List<Map<String, Object>> rows = db.queryForList("SELECT id,name,address FROM pickup_points WHERE id=? AND enabled=1", id);
        if (rows.isEmpty()) throw new ApiException(404, "自提点不存在");
        return ApiResponse.ok(rows.get(0));
    }

    /** 管理员查看全部商品和库存。 */
    @GetMapping("/api/admin/products")
    public ApiResponse<List<Map<String, Object>>> adminProducts(@RequestHeader(value="Authorization",required=false) String auth) {
        TokenUtil.requireAdmin(auth, jwtSecret);
        return ApiResponse.ok(db.queryForList("SELECT id,name,description,image_url AS imageUrl,price_cents AS priceCents,available_stock AS availableStock,enabled FROM products ORDER BY id DESC"));
    }

    /** 管理员创建商品。 */
    @PostMapping("/api/admin/products")
    public ApiResponse<Map<String, Object>> create(@RequestHeader(value="Authorization",required=false) String auth, @Valid @RequestBody ProductInput input) {
        TokenUtil.requireAdmin(auth, jwtSecret);
        Map<String, Object> values = new HashMap<>();
        values.put("name", input.name); values.put("description", input.description); values.put("image_url", input.imageUrl);
        values.put("price_cents", input.priceCents); values.put("available_stock", input.initialStock); values.put("enabled", true);
        Number id = new SimpleJdbcInsert(db).withTableName("products").usingGeneratedKeyColumns("id").executeAndReturnKey(values);
        return product(id.longValue());
    }

    /** 管理员修改商品资料和上架状态。 */
    @PutMapping("/api/admin/products/{id}")
    public ApiResponse<Map<String, Object>> update(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable long id, @Valid @RequestBody ProductInput input) {
        TokenUtil.requireAdmin(auth, jwtSecret);
        int changed = db.update("UPDATE products SET name=?,description=?,image_url=?,price_cents=?,enabled=? WHERE id=?", input.name, input.description, input.imageUrl, input.priceCents, input.enabled, id);
        if (changed == 0) throw new ApiException(404, "商品不存在");
        return ApiResponse.ok(db.queryForMap("SELECT id,name,description,image_url AS imageUrl,price_cents AS priceCents,available_stock AS availableStock,enabled FROM products WHERE id=?", id));
    }

    /** 管理员以增量方式调整库存。 */
    @PostMapping("/api/admin/products/{id}/stock")
    public ApiResponse<Map<String, Object>> adjustStock(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable long id, @RequestBody StockInput input) {
        TokenUtil.requireAdmin(auth, jwtSecret);
        if (input.delta == 0 || Math.abs((long) input.delta) > 100000) throw new ApiException(400, "库存调整数量不正确");
        int changed = db.update("UPDATE products SET available_stock=available_stock+? WHERE id=? AND available_stock+?>=0", input.delta, id, input.delta);
        if (changed == 0) throw new ApiException(409, "商品不存在或库存不足");
        return ApiResponse.ok(db.queryForMap("SELECT id,available_stock AS availableStock FROM products WHERE id=?", id));
    }

    /** 为订单预占商品库存。 */
    @PostMapping("/internal/reservations/{orderNo}")
    public ApiResponse<Void> reserve(@RequestHeader("X-Internal-Key") String key, @PathVariable String orderNo, @Valid @RequestBody ReservationInput input) {
        requireInternal(key);
        inventory.reserve(orderNo, input.productId, input.quantity);
        return ApiResponse.ok(null);
    }

    /** 为取消或失败的订单释放商品库存。 */
    @PostMapping("/internal/reservations/{orderNo}/release")
    public ApiResponse<Void> release(@RequestHeader("X-Internal-Key") String key, @PathVariable String orderNo, @Valid @RequestBody ReservationInput input) {
        requireInternal(key);
        inventory.release(orderNo, input.productId, input.quantity);
        return ApiResponse.ok(null);
    }

    /** 校验仅服务间共享的密钥。 */
    private void requireInternal(String key) {
        if (!MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) throw new ApiException(403, "禁止访问内部接口");
    }

    public static class ProductInput {
        @NotBlank(message="请输入商品名称") @Size(max=100) public String name;
        @Size(max=500) public String description;
        @Size(max=500) public String imageUrl;
        @NotNull @Min(value=1,message="价格必须大于零") public Long priceCents;
        @NotNull @Min(value=0,message="初始库存不能为负") public Integer initialStock;
        public boolean enabled = true;
    }
    public static class StockInput { public int delta; }
    public static class ReservationInput {
        @Min(1) public long productId;
        @Min(1) public int quantity;
    }
}
