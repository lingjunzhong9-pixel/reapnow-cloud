package com.reapnow.demand;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemandRequest(
        @NotBlank(message = "请填写地块名称") String plotName,
        String region,
        @NotNull(message = "请填写亩数")
        @DecimalMin(value = "0.1", message = "亩数至少 0.1")
        BigDecimal mu,
        @NotNull(message = "请填写期望单价") BigDecimal pricePerMu,
        @NotNull(message = "请选择麦子状态") CropStage cropStage,
        @NotNull(message = "请选择最迟收割日期") LocalDate deadline,
        Boolean lodged,
        String remark
) {
}
