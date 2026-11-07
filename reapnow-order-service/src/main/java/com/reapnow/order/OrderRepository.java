package com.reapnow.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<HarvestOrder, Long> {
    List<HarvestOrder> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<HarvestOrder> findByDemandId(Long demandId);
}
