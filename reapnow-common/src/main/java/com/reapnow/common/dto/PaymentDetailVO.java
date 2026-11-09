package com.reapnow.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 定金流水详情视图对象。
 *
 * 为什么需要这个 DTO：
 *   payments 表只存了 orderId，管理员看流水时无法知道「这笔钱是谁付给谁的、对应哪块地」。
 *   这些信息分散在订单服务（机手/农户/地块/金额）里，需要由支付服务通过 Feign 取回后组装。
 *   直接返回 Payment 实体做不到，所以单独定义一个聚合视图。
 *
 * 字段分三块：
 *   1. 流水本身：paymentNo / amount / type / channel / status / paidAt
 *   2. 关联订单：orderNo / orderStatus / 地块 / 亩数 / 单价 / 总额 / 定金
 *   3. 交易双方：payer（付款方=机手）+ payee（收款方=农户）
 */
public record PaymentDetailVO(
        // ---- 流水信息 ----
        Long id,
        String paymentNo,
        BigDecimal amount,
        String type,
        String channel,
        String status,
        LocalDateTime paidAt,

        // ---- 关联订单 ----
        Long orderId,
        String orderNo,
        String orderStatus,
        String plotName,
        String region,
        BigDecimal mu,
        BigDecimal pricePerMu,
        BigDecimal orderAmount,
        BigDecimal deposit,
        String machineModel,
        LocalDateTime grabAt,
        LocalDateTime serviceDate,

        // ---- 交易双方 ----
        Long payerId,
        String payerName,
        String payerPhone,
        String payeeName,
        String payeePhone
) {}
