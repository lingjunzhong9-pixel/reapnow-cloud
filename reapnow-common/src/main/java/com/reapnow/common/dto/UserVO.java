package com.reapnow.common.dto;

import java.math.BigDecimal;

/** 用户视图对象：跨服务传输用，不含密码 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String phone,
        String region,
        String role,
        BigDecimal creditScore,
        Boolean verified
) {}
