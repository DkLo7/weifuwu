package com.example.groupbuy.gateway;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Value("${app.jwt-secret}") private String jwtSecret;

    /** 校验需登录的路径，并拦截非管理员访问管理接口。 */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/orders") && !path.startsWith("/api/admin/") && !path.equals("/api/auth/me")) return chain.filter(exchange);
        try {
            Map<String, Object> claims = TokenUtil.verify(exchange.getRequest().getHeaders().getFirst("Authorization"), jwtSecret);
            if (path.startsWith("/api/admin/") && !"ADMIN".equals(claims.get("role"))) throw new ApiException(403, "没有管理权限");
            return chain.filter(exchange);
        } catch (ApiException error) {
            return error(exchange, error);
        }
    }

    /** 在路由前写出统一鉴权错误。 */
    private Mono<Void> error(ServerWebExchange exchange, ApiException error) {
        try {
            byte[] bytes = JSON.writeValueAsBytes(new ApiResponse<>(error.status, error.getMessage(), null));
            exchange.getResponse().setStatusCode(HttpStatus.valueOf(error.status));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ignored) {
            return Mono.error(error);
        }
    }

    /** 让鉴权在路由转发之前执行。 */
    @Override
    public int getOrder() { return -100; }
}
