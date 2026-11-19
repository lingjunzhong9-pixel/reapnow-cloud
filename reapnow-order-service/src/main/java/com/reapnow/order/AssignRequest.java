package com.reapnow.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调度员兜底指派请求。
 * 场景：需求临期无人抢，调度员在工作台看到后，直接点名某位已认证机手接单 ——
 * 与机手自助抢单走同一套扣减 / 建单 / 定金流程，只是发起方和承接方都由调度员指定。
 */
@Data
public class AssignRequest {

    @NotNull(message = "请选择要指派的需求")
    private Long demandId;

    @NotNull(message = "请选择承接的机手")
    private Long machineOwnerId;

    @NotNull(message = "请填写承接亩数")
    @DecimalMin(value = "0.1", message = "承接亩数至少 0.1")
    private BigDecimal mu;

    @NotBlank(message = "请填写作业机型")
    private String machineModel;

    /** 调度备注，会写进订单快照，答辩演示时能看到「谁指派的、为什么」 */
    private String note;
}
