package com.reapnow.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧的 JWT 验签工具。
 * 与 reapnow-common 中的 JwtUtil 保持同一密钥与算法；
 * 网关基于 WebFlux 不依赖 common（后者带 servlet web），故此处单独一份实现。
 */
public final class JwtUtil {

    private static final String DEFAULT_SECRET =
            "reapnow-course-demo-secret-key-please-override-in-production-0123456789";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
    }

    private static String resolveSecret() {
        String s = System.getenv("REAPNOW_JWT_SECRET");
        return (s == null || s.isBlank()) ? DEFAULT_SECRET : s;
    }

    public static Long parseUserId(String token) {
        Claims c = Jwts.parser().verifyWith(KEY).build()
                .parseSignedClaims(token).getPayload();
        return Long.valueOf(c.getSubject());
    }
}
