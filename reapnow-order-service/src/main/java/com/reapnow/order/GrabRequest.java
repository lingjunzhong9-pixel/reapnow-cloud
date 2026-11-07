package com.reapnow.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GrabRequest(
        @NotNull(message = "请选择要抢的作业需求") Long demandId,
        @NotNull(message = "请填写承接亩数")
        @DecimalMin(value = "0.1", message = "承接亩数至少 0.1")
        BigDecimal mu,
        /** 机手填写的机型，可空 */
        String machineModel
) {
}
