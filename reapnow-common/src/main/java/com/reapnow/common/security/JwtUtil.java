package com.reapnow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析。
 *
 * 为什么用 JWT 而不是内存 token：
 *   内存 token 只能被签发它的那一个进程认识，网关无法独立校验，
 *   每来一个请求都得再回用户服务问一次。换成 JWT 后签名自证，
 *   网关本地就能验签并取出 userId，这也是「网关做鉴权」的前提。
 *
 * 密钥通过环境变量 REAPNOW_JWT_SECRET 覆盖，课程演示用默认值。
 */
public final class JwtUtil {

    private static final String DEFAULT_SECRET =
            "reapnow-course-demo-secret-key-please-override-in-production-0123456789";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8));

    /** 有效期 7 天 */
    private static final long TTL_MILLIS = 7L * 24 * 3600 * 1000;

    private JwtUtil() {
    }

    private static String resolveSecret() {
        String s = System.getenv("REAPNOW_JWT_SECRET");
        return (s == null || s.isBlank()) ? DEFAULT_SECRET : s;
    }

    public static String create(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MILLIS))
                .signWith(KEY)
                .compact();
    }

    public static Long parseUserId(String token) {
        Claims c = Jwts.parser().verifyWith(KEY).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(c.getSubject());
    }
}
