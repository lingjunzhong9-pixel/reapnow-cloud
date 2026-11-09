package com.reapnow.payment;

import com.reapnow.common.BusinessException;
import org.apache.seata.spring.annotation.GlobalTransactional;
import com.reapnow.common.client.OrderClient;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.OrderVO;
import com.reapnow.common.dto.PaymentDetailVO;
import com.reapnow.common.dto.UserVO;
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
    private final UserClient userClient;

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

    // ==================================================================
    //  查询能力（验收清单 A-9「定金流水查询」）
    // ==================================================================

    /**
     * 按订单查定金流水。
     * 一个订单可能有多条流水（支付失败后重试、已退款后再开新单），所以返回列表而不是单条。
     * 新的排在前面，方便直接看到最近的支付尝试。
     */
    public java.util.List<Payment> listByOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByIdDesc(orderId);
    }

    /**
     * 后台定金流水列表：支持按状态筛选、分页。
     *
     * @param status 可选，SUCCESS / FAILED / REFUNDED / PENDING，传空表示全部
     * @param page   页码，从 1 开始
     * @param size   每页条数，最大 100（防止一次拉爆）
     */
    public org.springframework.data.domain.Page<Payment> listAll(String status, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(p - 1, s,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "id"));

        if (status != null && !status.isBlank()) {
            PaymentStatus st;
            try {
                st = PaymentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("流水状态不合法：" + status
                        + "，可选值 PENDING / SUCCESS / FAILED / REFUNDED");
            }
            return paymentRepository.findByStatus(st, pageable);
        }
        return paymentRepository.findAll(pageable);
    }

    /**
     * 定金流水详情：把「流水 + 订单 + 交易双方」聚合起来给后台看。
     *
     * <p>payments 表只存了 orderId，管理员光看流水号看不出这笔钱是谁付给谁的、对应哪块地。
     * 这里通过两次 Feign 调用补齐：
     * <ul>
     *   <li>OrderClient  取订单：机手、农户、地块、亩数、单价、总额、定金、订单状态</li>
     *   <li>UserClient   取机手手机号（订单里只有姓名，没有联系方式）</li>
     * </ul>
     * 支付方固定是机手（付定金的一方），收款方固定是农户。
     */
    public PaymentDetailVO detail(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("流水不存在：" + paymentId));

        OrderVO o = null;
        try {
            o = orderClient.byId(p.getOrderId()).getData();
        } catch (Exception ignored) {
            // 订单服务不可用时，详情页仍要能打开，只是订单区块显示为空
        }

        String payerPhone = null;
        if (o != null && o.ownerId() != null) {
            try {
                UserVO u = userClient.byId(o.ownerId()).getData();
                payerPhone = (u == null ? null : u.phone());
            } catch (Exception ignored) {
            }
        }

        return new PaymentDetailVO(
                p.getId(), p.getPaymentNo(), p.getAmount(),
                p.getType() == null ? null : p.getType().name(),
                p.getChannel(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getPaidAt(),

                p.getOrderId(),
                o == null ? null : o.orderNo(),
                o == null ? null : o.status(),
                o == null ? null : o.plotName(),
                o == null ? null : o.region(),
                o == null ? null : o.mu(),
                o == null ? null : o.pricePerMu(),
                o == null ? null : o.amount(),
                o == null ? null : o.deposit(),
                o == null ? null : o.machineModel(),
                o == null ? null : o.createdAt(),
                o == null ? null : o.serviceDate(),

                o == null ? null : o.ownerId(),
                o == null ? null : o.ownerName(),
                payerPhone,
                o == null ? null : o.farmerName(),
                o == null ? null : o.farmerPhone()
        );
    }
}
