package com.reapnow.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<HarvestOrder, Long> {
    List<HarvestOrder> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<HarvestOrder> findByDemandId(Long demandId);

    /** 行情统计用：按状态集合查订单（成交口径 = 定金已付 / 已完成） */
    List<HarvestOrder> findByStatusIn(List<OrderStatus> statuses);
}
