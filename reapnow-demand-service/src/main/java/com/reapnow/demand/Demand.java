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
