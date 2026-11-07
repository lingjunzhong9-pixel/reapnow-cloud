package com.reapnow.user;

import com.reapnow.common.security.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * 第 3 周改造：令牌从「内存 UUID」升级为 JWT。
 *
 * 好处：签名自证，网关和其它服务都能独立验签，不再需要回查本服务。
 * 本类保留为门面，业务代码不需要关心底层是内存还是 JWT。
 */
@Component
public class TokenStore {

    public String issue(Long userId) {
        return JwtUtil.create(userId);
    }

    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return JwtUtil.parseUserId(token);
        } catch (Exception e) {
            return null;   // 签名不对 / 过期 / 格式错误
        }
    }

    public Long require(String token) {
        Long uid = resolve(token);
        if (uid == null) {
            throw new com.reapnow.common.BusinessException(401, "未登录或登录已过期");
        }
        return uid;
    }

    /** JWT 无状态，登出由网关或前端丢弃令牌实现；此方法保留以兼容旧调用 */
    public void revoke(String token) {
        // no-op
    }
}
