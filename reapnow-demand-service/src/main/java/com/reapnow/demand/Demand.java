package com.reapnow.demand;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作业需求：农户发布的一条「我有多少亩地要在哪天之前收完」。
 * 这是本平台的核心实体 —— 抢单抢的就是它上面的 remainMu（剩余可接亩数）。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "demands")
public class Demand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String demandNo;

    /** 发布该需求的农户 */
    @Column(nullable = false)
    private Long farmerId;

    private String farmerName;

    private String farmerPhone;

    /** 地块名称，如「村东头三等地」 */
    @Column(nullable = false)
    private String plotName;

    /** 所在县市，如「安徽阜阳颍东区」 */
    private String region;

    /** 总亩数 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mu;

    /** 剩余可接亩数（抢单时原子扣减） */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal remainMu;

    /** 农户期望单价（元/亩） */
    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerMu;

    /** 麦子状态 */
    @Enumerated(EnumType.STRING)
    private CropStage cropStage;

    /** 最迟必须收完的日期 —— 通常是雨前那天 */
    private LocalDate deadline;

    /** 是否有倒伏（倒伏地块作业难度大、通常要加价） */
    private Boolean lodged;

    /**
     * 临期加急标记。
     * 截止前 48 小时（开启天气预警联动后为 72 小时）仍未被抢满的需求，
     * 由定时巡检任务（UrgentScanTask）自动打标，抢单大厅默认排最前。
     */
    private Boolean urgent;

    /**
     * 农户授权的临期自动加价上限（百分比，0~20）。
     * 截止日当天仍无人接单时，系统按该幅度一次性上调期望单价并重新曝光；
     * 0 或空表示不授权，绝不加价。
     */
    private Integer markupLimit;

    /** 本次授权加价是否已应用 —— 只加一次，防止巡检重复加价 */
    private Boolean markupApplied;

    private String remark;

    @Enumerated(EnumType.STRING)
    private DemandStatus status;

    private LocalDateTime createdAt;

    /** 距最迟收割日还有几天；负数表示已经逾期 */
    @Transient
    public long getDaysLeft() {
        return deadline == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }
}
