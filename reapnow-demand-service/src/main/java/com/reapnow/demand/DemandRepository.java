package com.reapnow.demand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface DemandRepository extends JpaRepository<Demand, Long>,
        JpaSpecificationExecutor<Demand> {

    /**
     * 原子扣减剩余亩数 —— 本项目并发安全的核心。
     *
     * 只有「剩余亩数 >= 本次抢单亩数」且「需求仍处于可抢状态」时才会更新成功。
     * 返回受影响行数为 0 说明这单已经被别人抢掉了，直接拒绝，不做「先查再改」。
     *
     * 第 7 周拆微服务后，这个条件更新会替换为 Seata 全局事务 + Redis 分布式锁。
     */
    @Modifying
    @Query("update Demand d set d.remainMu = d.remainMu - :mu "
            + "where d.id = :id and d.status = com.reapnow.demand.DemandStatus.OPEN "
            + "and d.remainMu >= :mu")
    int deductRemainMu(@Param("id") Long id, @Param("mu") BigDecimal mu);

    /** 需求被抢满后置为 FULL */
    @Modifying
    @Query("update Demand d set d.status = com.reapnow.demand.DemandStatus.FULL "
            + "where d.id = :id and d.remainMu <= 0")
    int markFullIfExhausted(@Param("id") Long id);
}
