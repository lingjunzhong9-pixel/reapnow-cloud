package com.reapnow.order;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.apache.seata.spring.annotation.GlobalTransactional;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.reapnow.common.BusinessException;
import com.reapnow.common.client.DemandClient;
import com.reapnow.common.client.PaymentClient;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.DemandVO;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单服务。
 * 第 2 周改造要点：
 *  1) 用户校验、需求查询改为跨服务调用（UserClient / DemandClient）；
 *  2) 「扣亩数 + 建单」现在是跨服务操作，本地 @Transactional 只能保证订单侧一致，
 *     第 7 周接入 Seata AT 后由全局事务保证两侧同成败；
 *  3) 取消时的退款改为调用支付服务（PaymentClient）。
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class OrderService {

    /** 定金比例 30% */
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.30");

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final DemandClient demandClient;
    private final PaymentClient paymentClient;

    public HarvestOrder getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
    }

    public UserVO requireUser(Long userId) {
        UserVO u = userClient.byId(userId).getData();
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        return u;
    }

    @GlobalTransactional(name = "reapnow-grab", rollbackFor = Exception.class)
    @Transactional
    @SentinelResource(value = "grab", blockHandler = "grabBlocked")
    public HarvestOrder grab(Long ownerId, GrabRequest req) {
        UserVO owner = requireUser(ownerId);
        if (!"MACHINE_OWNER".equals(owner.role()) && !"ADMIN".equals(owner.role())) {
            throw new BusinessException("只有农机手账号可以抢单");
        }
        if ("MACHINE_OWNER".equals(owner.role()) && !Boolean.TRUE.equals(owner.verified())) {
            throw new BusinessException("请先完成实名与跨区作业证审核后再抢单");
        }

        DemandVO d = demandClient.byId(req.demandId()).getData();
        if (d == null) {
            throw new BusinessException("作业需求不存在");
        }
        if (req.mu().compareTo(d.remainMu()) > 0) {
            throw new BusinessException("承接亩数超过剩余可接亩数（剩余 " + d.remainMu() + " 亩）");
        }

        // 跨服务原子扣减：由需求服务用条件更新保证不超卖
        Boolean ok = demandClient.deduct(d.id(), req.mu()).getData();
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessException("手慢了，这块地已经被别的机手抢走了");
        }

        BigDecimal amount = req.mu().multiply(d.pricePerMu()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = amount.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);

        HarvestOrder o = new HarvestOrder();
        o.setOrderNo("RC" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        o.setDemandId(d.id());
        o.setPlotName(d.plotName());
        o.setRegion(d.region());
        o.setFarmerName(d.farmerName());
        o.setFarmerPhone(d.farmerPhone());
        o.setOwnerId(ownerId);
        o.setOwnerName(owner.nickname() == null ? owner.username() : owner.nickname());
        o.setMachineModel(req.machineModel());
        o.setMu(req.mu());
        o.setPricePerMu(d.pricePerMu());
        o.setAmount(amount);
        o.setDeposit(deposit);
        o.setStatus(OrderStatus.PENDING_DEPOSIT);
        o.setSource("GRAB");
        o.setServiceDate(d.deadline() == null ? null : d.deadline().atTime(8, 0));
        o.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(o);
    }

    /**
     * 调度员兜底指派：与机手自助抢单共用同一套「原子扣减 + 建单 + 定金」流程，
     * 区别只在两点：
     *   1) 发起方必须是 DISPATCHER / ADMIN；
     *   2) 承接机手由调度员点名（必须是已认证的农机手账号）。
     * 订单 source 标记为 ASSIGN，机手端「我的接单」会显示「调度指派」角标。
     */
    @GlobalTransactional(name = "reapnow-assign", rollbackFor = Exception.class)
    @Transactional
    public HarvestOrder assign(Long dispatcherId, AssignRequest req) {        UserVO dispatcher = requireUser(dispatcherId);
        if (!"DISPATCHER".equals(dispatcher.role()) && !"ADMIN".equals(dispatcher.role())) {
            throw new BusinessException("只有调度员可以指派订单");
        }

        UserVO owner = requireUser(req.getMachineOwnerId());
        if (!"MACHINE_OWNER".equals(owner.role())) {
            throw new BusinessException("被指派的账号不是农机手");
        }
        if (!Boolean.TRUE.equals(owner.verified())) {
            throw new BusinessException("被指派的机手尚未完成实名与跨区作业证审核");
        }

        DemandVO d = demandClient.byId(req.getDemandId()).getData();
        if (d == null) {
            throw new BusinessException("作业需求不存在");
        }
        if (req.getMu().compareTo(d.remainMu()) > 0) {
            throw new BusinessException("指派亩数超过剩余可接亩数（剩余 " + d.remainMu() + " 亩）");
        }

        Boolean ok = demandClient.deduct(d.id(), req.getMu()).getData();
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessException("手慢了，这块地刚被别的机手抢走，请刷新后重试");
        }

        BigDecimal amount = req.getMu().multiply(d.pricePerMu()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = amount.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);

        HarvestOrder o = new HarvestOrder();
        o.setOrderNo("RC" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        o.setDemandId(d.id());
        o.setPlotName(d.plotName());
        o.setRegion(d.region());
        o.setFarmerName(d.farmerName());
        o.setFarmerPhone(d.farmerPhone());
        o.setOwnerId(owner.id());
        o.setOwnerName(owner.nickname() == null ? owner.username() : owner.nickname());
        o.setMachineModel(req.getMachineModel());
        o.setMu(req.getMu());
        o.setPricePerMu(d.pricePerMu());
        o.setAmount(amount);
        o.setDeposit(deposit);
        o.setStatus(OrderStatus.PENDING_DEPOSIT);
        o.setSource("ASSIGN");
        o.setAssignBy(dispatcher.nickname() == null ? dispatcher.username() : dispatcher.nickname());
        o.setAssignNote(req.getNote());
        o.setServiceDate(d.deadline() == null ? null : d.deadline().atTime(8, 0));
        o.setCreatedAt(LocalDateTime.now());
        HarvestOrder saved = orderRepository.save(o);
        log.info("调度指派：{} 将需求[{}] {} 亩指派给机手 {}，订单 {}，备注：{}",
                o.getAssignBy(), o.getDemandId(), o.getMu(),
                o.getOwnerName(), o.getOrderNo(),
                req.getNote() == null || req.getNote().isBlank() ? "（无）" : req.getNote());
        return saved;
    }

    /**
     * 行情参考价：按近 30 天「已成交」订单（定金已付 / 已完成）的单价聚合。
     * 返回中位数与四分位 —— 发布表单用来给农户定价锚点，抢单大厅用来打「高于行情」标签。
     * 样本不足 3 单时返回 insufficient=true，前端退化为仅展示样本数。
     */
    public Map<String, Object> marketPrice(String region) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<HarvestOrder> deals = orderRepository.findByStatusIn(
                        List.of(OrderStatus.DEPOSIT_PAID, OrderStatus.DONE))
                .stream()
                .filter(o -> o.getCreatedAt() == null || !o.getCreatedAt().isBefore(since))
                .toList();
        if (region != null && !region.isBlank()) {
            String key = region.trim();
            deals = deals.stream()
                    .filter(o -> o.getRegion() != null && o.getRegion().contains(key))
                    .toList();
        }
        List<Double> prices = deals.stream()
                .filter(o -> o.getPricePerMu() != null)
                .map(o -> o.getPricePerMu().doubleValue())
                .sorted()
                .toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sample", prices.size());
        m.put("insufficient", prices.size() < 3);
        if (prices.isEmpty()) {
            return m;
        }
        m.put("median", round(q(prices, 0.50)));
        m.put("q1", round(q(prices, 0.25)));
        m.put("q3", round(q(prices, 0.75)));
        m.put("min", round(prices.get(0)));
        m.put("max", round(prices.get(prices.size() - 1)));
        return m;
    }

    private double q(List<Double> sorted, double p) {
        int n = sorted.size();
        double pos = p * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(lo + 1, n - 1);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (pos - lo);
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 抢单被限流 / 熔断时的兜底方法。
     * 签名要求：与原方法一致，末尾多一个 BlockException。
     * 返回给用户的是「稍后再试」，而不是 500 异常。
     */
    public HarvestOrder grabBlocked(Long ownerId, GrabRequest req, BlockException ex) {
        throw new BusinessException("当前抢单的人太多了，请 3 秒后再试（已限流）");
    }

    public List<HarvestOrder> mine(Long ownerId) {
        return orderRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public List<HarvestOrder> byDemand(Long demandId) {
        return orderRepository.findByDemandId(demandId);
    }

    @Transactional
    public HarvestOrder cancel(Long ownerId, Long orderId) {
        HarvestOrder o = getById(orderId);
        if (!o.getOwnerId().equals(ownerId)) {
            throw new BusinessException("无权操作该订单");
        }
        if (o.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消");
        }
        if (o.getStatus() == OrderStatus.DONE) {
            throw new BusinessException("已完成的订单不可取消");
        }
        if (o.getStatus() == OrderStatus.DEPOSIT_PAID) {
            paymentClient.refund(orderId);
        }
        o.setStatus(OrderStatus.CANCELLED);
        demandClient.release(o.getDemandId(), o.getMu());
        return orderRepository.save(o);
    }

    /** 支付服务回调：定金已付，订单转履约中 */
    @Transactional
    public boolean markPaid(Long orderId) {
        HarvestOrder o = getById(orderId);
        if (o.getStatus() != OrderStatus.PENDING_DEPOSIT) {
            return false;
        }
        o.setStatus(OrderStatus.DEPOSIT_PAID);
        o.setPaidAt(LocalDateTime.now());
        orderRepository.save(o);
        return true;
    }
}
