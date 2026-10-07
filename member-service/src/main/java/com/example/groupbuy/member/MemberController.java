package com.example.groupbuy.member;

import com.example.groupbuy.common.ApiException;
import com.example.groupbuy.common.ApiResponse;
import com.example.groupbuy.common.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class MemberController {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Value("${app.jwt-secret}") private String jwtSecret;
    @Value("${app.admin-password}") private String adminPassword;

    /** 注入用户数据库。 */
    public MemberController(JdbcTemplate db) { this.db = db; }

    /** 首次启动时创建本地演示管理员。 */
    @PostConstruct
    public void seedAdmin() {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM members WHERE username='admin'", Integer.class);
        if (count != null && count == 0) db.update("INSERT INTO members(username,password_hash,role) VALUES (?,?,?)", "admin", encoder.encode(adminPassword), "ADMIN");
    }

    /** 注册普通用户，禁止从请求中指定角色。 */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody Credentials input) {
        try {
            db.update("INSERT INTO members(username,password_hash,role) VALUES (?,?,?)", input.username.trim(), encoder.encode(input.password), "USER");
        } catch (DuplicateKeyException error) {
            throw new ApiException(409, "用户名已存在");
        }
        return login(input);
    }

    /** 验证密码并签发令牌。 */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody Credentials input) {
        java.util.List<Map<String, Object>> rows = db.queryForList("SELECT id,username,password_hash,role FROM members WHERE username=?", input.username.trim());
        if (rows.isEmpty() || !encoder.matches(input.password, (String) rows.get(0).get("password_hash"))) throw new ApiException(401, "用户名或密码错误");
        Map<String, Object> member = rows.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("token", TokenUtil.issue(((Number) member.get("id")).longValue(), (String) member.get("role"), jwtSecret));
        result.put("username", member.get("username"));
        result.put("role", member.get("role"));
        return ApiResponse.ok(result);
    }

    /** 返回当前令牌的用户资料。 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader(value="Authorization", required=false) String authorization) {
        long userId = TokenUtil.userId(authorization, jwtSecret);
        return ApiResponse.ok(db.queryForMap("SELECT id,username,role FROM members WHERE id=?", userId));
    }

    public static class Credentials {
        @NotBlank(message="请输入用户名") @Size(min=3,max=40,message="用户名长度为 3 到 40 位") public String username;
        @NotBlank(message="请输入密码") @Size(min=6,max=72,message="密码至少 6 位") public String password;
    }
}
