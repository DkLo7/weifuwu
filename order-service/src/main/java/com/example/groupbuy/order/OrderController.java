package com.example.groupbuy.order;

import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@RestController
public class OrderController {
    private final OrderService orders;
    @Value("${app.jwt-secret}") private String jwtSecret;

    /** 注入订单业务。 */
    public OrderController(OrderService orders) { this.orders = orders; }

    /** 创建订单。 */
    @PostMapping("/api/orders")
    public ApiResponse<Map<String, Object>> create(@RequestHeader(value="Authorization",required=false) String auth, @Valid @RequestBody OrderInput input) {
        return ApiResponse.ok(orders.create(auth, input));
    }

    /** 查询当前用户的订单列表。 */
    @GetMapping("/api/orders")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader(value="Authorization",required=false) String auth) { return ApiResponse.ok(orders.mine(auth)); }

    /** 查询当前用户的单个订单。 */
    @GetMapping("/api/orders/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable String orderNo) {
        return ApiResponse.ok(orders.findOwned(TokenUtil.userId(auth, jwtSecret), orderNo));
    }

    /** 模拟支付订单。 */
    @PostMapping("/api/orders/{orderNo}/pay")
    public ApiResponse<Map<String, Object>> pay(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable String orderNo) { return ApiResponse.ok(orders.pay(auth, orderNo)); }

    /** 取消未支付订单。 */
    @PostMapping("/api/orders/{orderNo}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable String orderNo) { return ApiResponse.ok(orders.cancel(auth, orderNo)); }

    /** 管理员查询订单。 */
    @GetMapping("/api/admin/orders")
    public ApiResponse<List<Map<String, Object>>> all(@RequestHeader(value="Authorization",required=false) String auth) { return ApiResponse.ok(orders.all(auth)); }

    /** 管理员核销提货码。 */
    @PostMapping("/api/admin/pickups")
    public ApiResponse<Map<String, Object>> pickup(@RequestHeader(value="Authorization",required=false) String auth, @Valid @RequestBody PickupInput input) { return ApiResponse.ok(orders.pickup(auth, input.pickupCode)); }

    public static class OrderInput {
        @Min(1) public long productId;
        @Min(1) @Max(99) public int quantity;
        @Min(1) public long pickupPointId;
        @NotBlank @Size(max=64) public String clientKey;
    }
    public static class PickupInput { @NotBlank public String pickupCode; }
}
