package com.example.groupbuy.order;

import com.example.groupbuy.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name="catalog-service")
public interface CatalogClient {
    /** 查询商品快照。 */
    @GetMapping("/api/products/{id}")
    ApiResponse<Map<String, Object>> product(@PathVariable("id") long id);

    /** 校验自提点并读取名称。 */
    @GetMapping("/internal/pickup-points/{id}")
    ApiResponse<Map<String, Object>> pickupPoint(@RequestHeader("X-Internal-Key") String key, @PathVariable("id") long id);

    /** 按订单号预占库存。 */
    @PostMapping("/internal/reservations/{orderNo}")
    ApiResponse<Void> reserve(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderNo") String orderNo, @RequestBody ReservationInput input);

    /** 按订单号释放库存。 */
    @PostMapping("/internal/reservations/{orderNo}/release")
    ApiResponse<Void> release(@RequestHeader("X-Internal-Key") String key, @PathVariable("orderNo") String orderNo, @RequestBody ReservationInput input);

    class ReservationInput {
        public long productId;
        public int quantity;

        /** 创建库存请求。 */
        public ReservationInput(long productId, int quantity) { this.productId = productId; this.quantity = quantity; }
    }
}
