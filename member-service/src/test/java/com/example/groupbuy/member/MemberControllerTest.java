package com.example.groupbuy.member;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberControllerTest {
    private JdbcTemplate db;
    private MemberController controller;

    /** 创建隔离的控制器和数据库替身。 */
    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        controller = new MemberController(db);
        ReflectionTestUtils.setField(controller, "jwtSecret", "unit-test-secret");
        ReflectionTestUtils.setField(controller, "adminPassword", "Admin@123456");
    }

    /** 注册后只获得普通用户令牌。 */
    @Test
    void registerCreatesUserRole() {
        Map<String, Object> row = member("alice", "USER");
        when(db.queryForList(anyString(), eq("alice"))).thenReturn(Collections.singletonList(row));
        MemberController.Credentials input = credentials("alice", "secret123");
        String token = (String) controller.register(input).data.get("token");
        assertEquals("USER", TokenUtil.verify("Bearer " + token, "unit-test-secret").get("role"));
        verify(db).update(anyString(), eq("alice"), anyString(), eq("USER"));
        assertThrows(ApiException.class, () -> TokenUtil.requireAdmin("Bearer " + token, "unit-test-secret"));
    }

    /** 错误密码不能签发令牌。 */
    @Test
    void loginRejectsWrongPassword() {
        when(db.queryForList(anyString(), eq("alice"))).thenReturn(Collections.singletonList(member("alice", "USER")));
        assertEquals(401, assertThrows(ApiException.class, () -> controller.login(credentials("alice", "wrong123"))).status);
    }

    /** 管理员仅在不存在时初始化。 */
    @Test
    void seedAdminOnlyOnce() {
        when(db.queryForObject(anyString(), eq(Integer.class))).thenReturn(0, 1);
        controller.seedAdmin();
        controller.seedAdmin();
        verify(db, times(1)).update(anyString(), eq("admin"), anyString(), eq("ADMIN"));
    }

    /** 构造测试用户数据。 */
    private Map<String, Object> member(String username, String role) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 7L); row.put("username", username); row.put("role", role);
        row.put("password_hash", new BCryptPasswordEncoder().encode("secret123"));
        return row;
    }

    /** 构造登录请求。 */
    private MemberController.Credentials credentials(String username, String password) {
        MemberController.Credentials input = new MemberController.Credentials();
        input.username = username; input.password = password;
        return input;
    }
}
