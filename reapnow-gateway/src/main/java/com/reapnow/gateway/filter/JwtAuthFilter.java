package com.reapnow.gateway.filter;

import com.reapnow.gateway.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局 JWT 鉴权过滤器。
 *
 * 规则：
 *   1) 白名单直接放行（登录、注册、抢单大厅的公开查询）；
 *   2) 其余请求必须带 X-Token，网关本地验签（不访问用户服务）；
 *   3) 验签通过后把解析出的 userId 注入 X-User-Id 请求头。
 *
 * 关于 X-User-Id 的定位（重要，避免误解）：
 *   本项目的业务服务**并不直接信任** X-User-Id，而是各自拿 token 向用户服务校验身份。
 *   这样做是因为服务端口（8081—8084）在开发环境是对外可达的，
 *   如果服务只信 X-User-Id，绕过网关直连服务就能伪造任意用户。
 *   所以目前 X-User-Id 只作为「调用链路标记」用于日志排查，
 *   真正的身份来源始终是 token。后续若把服务端口收回内网，
 *   可改为信任 X-User-Id，省掉每个请求一次远程校验。
 *
 * 注意：这里必须用 header() 覆盖而不是追加 —— 客户端传来的 X-User-Id 会被真实值替换，
 * 不会被伪造值污染（见 F5 安全边界演示）。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 无需登录即可访问 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单
        if (WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        // 抢单大厅：GET /api/demands 与 GET /api/demands/{id} 允许匿名浏览
        if (HttpMethod.GET.equals(request.getMethod()) && path.startsWith("/api/demands")) {
            boolean isInternal = path.contains("/deduct") || path.contains("/release");
            boolean isMine = path.endsWith("/mine");
            if (!isInternal && !isMine) {
                return chain.filter(exchange);
            }
        }

        String token = request.getHeaders().getFirst("X-Token");
        if (token == null || token.isBlank()) {
            return reject(exchange, "未登录，请先登录后再操作");
        }

        try {
            Long userId = JwtUtil.parseUserId(token);
            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return reject(exchange, "登录已过期，请重新登录");
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, String msg) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + msg + "\",\"data\":null}";
        DataBuffer buf = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buf));
    }

    @Override
    public int getOrder() {
        return -100;   // 早于路由过滤器执行
    }
}
