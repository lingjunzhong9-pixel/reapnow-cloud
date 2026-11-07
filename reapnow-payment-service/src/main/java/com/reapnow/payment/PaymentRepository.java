package com.reapnow.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndTypeAndStatus(Long orderId, PaymentType type, PaymentStatus status);

    List<Payment> findByOrderId(Long orderId);

    /** 按订单查流水，新的在前（同一订单可能有「失败→成功→已退款」多条） */
    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    /** 后台流水查询：按状态筛选 + 分页 */
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
