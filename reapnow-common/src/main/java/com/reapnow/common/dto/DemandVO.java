package com.reapnow.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 作业需求视图对象：demand-service 对外暴露，order-service 消费 */
public record DemandVO(
        Long id,
        String demandNo,
        Long farmerId,
        String farmerName,
        String farmerPhone,
        String plotName,
        String region,
        BigDecimal mu,
        BigDecimal remainMu,
        BigDecimal pricePerMu,
        String cropStage,
        LocalDate deadline,
        Boolean lodged,
        String remark,
        String status,
        LocalDateTime createdAt
) {}
