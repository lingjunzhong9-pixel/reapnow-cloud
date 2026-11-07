package com.reapnow.payment;

import com.reapnow.common.BusinessException;
import org.apache.seata.spring.annotation.GlobalTransactional;
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

    /**
     * 支付定金。这是本项目的第二个分布式事务场景：
     *   支付服务写 payments 流水  +  订单服务把订单置为 DEPOSIT_PAID
     * 两步跨服务，任一失败都必须整体回滚 —— 否则会出现「钱扣了订单没变」或反之。
     *
     * @param simulateFail 仅用于演示：true 时写完流水后故意抛异常，验证整笔回滚
     */
    @GlobalTransactional(name = "reapnow-pay-deposit", rollbackFor = Exception.class)
    @Transactional
    public Payment payDeposit(Long ownerId, Long orderId, String channel, boolean simulateFail) {
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

        if (simulateFail) {
            // 故障注入：流水已写库，这里抛异常，观察 payments 表是否回滚
            throw new BusinessException("【故障注入】支付流水已写入，现在故意失败，即将触发全局回滚");
        }

        // 回调订单服务：跨服务写操作，由 Seata 全局事务覆盖
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
