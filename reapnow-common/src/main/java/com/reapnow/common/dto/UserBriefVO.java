package com.reapnow.common.dto;

import java.math.BigDecimal;

/**
 * 用户简要信息（脱敏）。
 *
 * 为什么需要它：
 *   调度工作台需要拉「已认证机手」列表来做兜底指派，但完整的 {@link UserVO}
 *   带手机号，一旦这个接口对任意登录用户开放，等于把全平台手机号导出去。
 *   这里只保留指派要用的字段，**不含 phone**。
 */
public record UserBriefVO(
        Long id,
        String username,
        String nickname,
        String region,
        String role,
        BigDecimal creditScore,
        Boolean verified
) {}
