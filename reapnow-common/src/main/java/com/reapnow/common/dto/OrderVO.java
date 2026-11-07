package com.reapnow.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单视图对象：order-service 对外暴露，payment-service 消费 */
public record OrderVO(
        Long id,
        String orderNo,
        Long demandId,
        String plotName,
        String region,
        String farmerName,
        String farmerPhone,
        Long ownerId,
        String ownerName,
        String machineModel,
        BigDecimal mu,
        BigDecimal pricePerMu,
        BigDecimal amount,
        BigDecimal deposit,
        String status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime serviceDate
) {}
