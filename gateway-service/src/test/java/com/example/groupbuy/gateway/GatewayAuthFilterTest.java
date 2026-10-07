package com.example.groupbuy.gateway;

import com.example.groupbuy.common.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayAuthFilterTest {
    private GatewayAuthFilter filter;
    private GatewayFilterChain chain;

    /** 创建网关过滤器和模拟路由链。 */
    @BeforeEach
    void setUp() {
        filter = new GatewayAuthFilter();
        chain = mock(GatewayFilterChain.class);
        ReflectionTestUtils.setField(filter, "jwtSecret", "unit-test-secret");
    }

    /** 未登录请求不能访问订单接口。 */
    @Test
    void orderNeedsLogin() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    /** 普通用户不能访问管理接口。 */
    @Test
    void adminNeedsRole() {
        String token = TokenUtil.issue(7, "USER", "unit-test-secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/orders").header("Authorization", "Bearer " + token));
        filter.filter(exchange, chain).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    /** 公开商品接口可以直接转发。 */
    @Test
    void publicProductsPass() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }

    /** 管理员令牌可以访问管理接口。 */
    @Test
    void adminTokenPasses() {
        String token = TokenUtil.issue(1, "ADMIN", "unit-test-secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/orders").header("Authorization", "Bearer " + token));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
        verify(chain).filter(exchange);
    }
}
