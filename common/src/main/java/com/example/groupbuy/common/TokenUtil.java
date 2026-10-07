package com.example.groupbuy.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class TokenUtil {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 禁止实例化工具类。 */
    private TokenUtil() {}

    /** 生成包含用户身份和过期时间的签名令牌。 */
    public static String issue(long userId, String role, String secret) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("uid", userId);
            payload.put("role", role);
            payload.put("exp", System.currentTimeMillis() + 86400000L);
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(payload));
            return body + "." + sign(body, secret);
        } catch (Exception error) {
            throw new IllegalStateException("令牌生成失败", error);
        }
    }

    /** 校验签名与有效期并返回令牌内容。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> verify(String authorization, String secret) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ApiException(401, "请先登录");
        try {
            String[] parts = authorization.substring(7).split("\\.");
            if (parts.length != 2 || !MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8), sign(parts[0], secret).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            Map<String, Object> data = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            if (((Number) data.get("exp")).longValue() <= System.currentTimeMillis()) throw new IllegalArgumentException();
            return data;
        } catch (Exception error) {
            throw new ApiException(401, "登录已失效，请重新登录");
        }
    }

    /** 返回当前登录用户编号。 */
    public static long userId(String authorization, String secret) {
        return ((Number) verify(authorization, secret).get("uid")).longValue();
    }

    /** 校验管理员身份。 */
    public static void requireAdmin(String authorization, String secret) {
        if (!"ADMIN".equals(verify(authorization, secret).get("role"))) throw new ApiException(403, "没有管理权限");
    }

    /** 对令牌正文计算 HMAC-SHA256。 */
    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
