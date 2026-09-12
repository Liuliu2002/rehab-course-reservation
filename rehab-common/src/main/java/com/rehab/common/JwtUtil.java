package com.rehab.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与解析工具。
 * token 中只保存用户 ID、角色和过期时间，不保存密码等敏感信息。
 * 生产环境必须通过 REHAB_JWT_SECRET 注入独立密钥，开发默认值仅用于本地启动。
 */
public class JwtUtil {
    private static final String DEV_SECRET = "local-development-only-change-with-REHAB_JWT_SECRET";
    private static final long DEFAULT_ACCESS_EXPIRE_MILLIS = 1000L * 60 * 15;

    /**
     * 兼容旧调用；新代码应显式传入会话 ID 和配置化的有效期。
     */
    @Deprecated
    public static String createToken(Long id, String role) {
        return createAccessToken(id, role, null, DEFAULT_ACCESS_EXPIRE_MILLIS);
    }

    public static String createAccessToken(Long id, String role, String sessionId, long expireMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("role", role);
        claims.put("type", "access");
        if (sessionId != null) {
            claims.put("sid", sessionId);
        }
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(SignatureAlgorithm.HS256, secret())
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser().setSigningKey(secret()).parseClaimsJws(token).getBody();
    }

    private static String secret() {
        String secret = System.getenv("REHAB_JWT_SECRET");
        return secret == null || secret.trim().isEmpty() ? DEV_SECRET : secret;
    }
}
