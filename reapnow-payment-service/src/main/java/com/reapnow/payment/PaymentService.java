package com.reapnow.payment;

import com.reapnow.common.BusinessException;
import com.reapnow.common.client.OrderClient;
import com.reapnow.common.dto.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 支付服务。
 * 第 2 周改造：订单信息不再直接查 orderRepository，改为 OrderClient 远程获取；
 * 支付成功后回调订单服务置为履约中。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;

    @Transactional
    public Payment payDeposit(Long ownerId, Long orderId, String channel) {
        OrderVO o = orderClient.byId(orderId).getData();
        if (o == null) {
            throw new BusinessException("订单不存在");
        }
        if (!o.ownerId().equals(ownerId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (!"PENDING_DEPOSIT".equals(o.status())) {
            throw new BusinessException("订单状态不可支付定金");
        }

        Optional<Payment> existing = paymentRepository
                .findByOrderIdAndTypeAndStatus(orderId, PaymentType.DEPOSIT, PaymentStatus.SUCCESS);
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment p = new Payment();
        p.setPaymentNo("PY" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        p.setOrderId(orderId);
        p.setAmount(o.deposit());
        p.setType(PaymentType.DEPOSIT);
        p.setChannel(channel == null || channel.isBlank() ? "MOCK" : channel);
        p.setStatus(PaymentStatus.SUCCESS);
        p.setPaidAt(LocalDateTime.now());
        paymentRepository.save(p);

        // 回调订单服务：跨服务写操作，第 7 周由 Seata 全局事务覆盖
        orderClient.markPaid(orderId);
        return p;
    }

    @Transactional
    public boolean refund(Long orderId) {
        return paymentRepository
                .findByOrderIdAndTypeAndStatus(orderId, PaymentType.DEPOSIT, PaymentStatus.SUCCESS)
                .map(p -> {
                    p.setStatus(PaymentStatus.REFUNDED);
                    paymentRepository.save(p);
                    return true;
                })
                .orElse(false);
    }
}
