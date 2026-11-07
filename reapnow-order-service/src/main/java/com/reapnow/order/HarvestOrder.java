package com.reapnow.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抢单订单：机手抢下某块地之后形成的履约单。
 * 定金付清前处于 PENDING_DEPOSIT，超时未付由 RocketMQ 延迟消息释放亩数（第 7 周接入）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "harvest_orders")
public class HarvestOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNo;

    @Column(nullable = false)
    private Long demandId;

    // ---- 下单时的快照字段，避免列表查询反复关联 ----
    private String plotName;
    private String region;
    private String farmerName;
    private String farmerPhone;

    @Column(nullable = false)
    private Long ownerId;

    private String ownerName;

    /** 机手作业机型，如「雷沃谷神 9 公斤/秒」 */
    private String machineModel;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mu;

    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerMu;

    /** 订单总额 = 亩数 × 单价 */
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** 定金 = 总额 × 30%，用于约束履约 */
    @Column(precision = 12, scale = 2)
    private BigDecimal deposit;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    /** 预计到场日期 */
    private LocalDateTime serviceDate;
}
