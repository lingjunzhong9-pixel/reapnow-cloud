package com.reapnow.demand;

import com.reapnow.common.BusinessException;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 需求服务。
 * 第 2 周改造：原先直接查 UserRepository 的地方，改为通过 Feign 调用户服务，
 * 服务之间只传 ID 与 VO，不共享数据库表结构。
 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final DemandRepository demandRepository;
    private final UserClient userClient;

    public Demand getById(Long id) {
        return demandRepository.findById(id)
                .orElseThrow(() -> new BusinessException("作业需求不存在"));
    }

    @Transactional
    public Demand create(Long farmerId, DemandRequest req) {
        UserVO farmer = userClient.byId(farmerId).getData();
        if (farmer == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"FARMER".equals(farmer.role()) && !"ADMIN".equals(farmer.role())) {
            throw new BusinessException("只有农户账号可以发布作业需求");
        }
        if (req.deadline().isBefore(LocalDate.now())) {
            throw new BusinessException("最迟收割日期不能早于今天");
        }

        Demand d = new Demand();
        d.setDemandNo("DM" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        d.setFarmerId(farmerId);
        d.setFarmerName(farmer.nickname() == null ? farmer.username() : farmer.nickname());
        d.setFarmerPhone(farmer.phone());
        d.setPlotName(req.plotName());
        d.setRegion(req.region() == null || req.region().isBlank() ? farmer.region() : req.region());
        d.setMu(req.mu());
        d.setRemainMu(req.mu());
        d.setPricePerMu(req.pricePerMu());
        d.setCropStage(req.cropStage());
        d.setDeadline(req.deadline());
        d.setLodged(Boolean.TRUE.equals(req.lodged()));
        d.setRemark(req.remark());
        d.setStatus(DemandStatus.OPEN);
        d.setCreatedAt(LocalDateTime.now());
        return demandRepository.save(d);
    }

    public Page<Demand> search(String status, String region, String keyword,
                               String sort, int page, int size) {
        Specification<Demand> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                try {
                    ps.add(cb.equal(root.get("status"), DemandStatus.valueOf(status.toUpperCase())));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (region != null && !region.isBlank()) {
                ps.add(cb.like(root.get("region"), "%" + region + "%"));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(
                        cb.like(root.get("plotName"), like),
                        cb.like(root.get("region"), like),
                        cb.like(root.get("remark"), like)
                ));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        Sort s = switch (sort == null ? "" : sort) {
            case "priceDesc" -> Sort.by("pricePerMu").descending();
            case "muDesc" -> Sort.by("mu").descending();
            case "newest" -> Sort.by("createdAt").descending();
            default -> Sort.by("deadline").ascending().and(Sort.by("pricePerMu").descending());
        };
        return demandRepository.findAll(spec, PageRequest.of(Math.max(0, page - 1), size, s));
    }

    public List<Demand> mine(Long farmerId) {
        return demandRepository.findAll((root, query, cb) ->
                        cb.equal(root.get("farmerId"), farmerId),
                Sort.by("createdAt").descending());
    }

    /**
     * 原子扣减剩余亩数。条件更新保证并发正确性。
     * 拆微服务后入口是 order-service 的 Feign 调用，事务边界在订单侧。
     */
    @Transactional
    public boolean tryDeduct(Long demandId, BigDecimal mu) {
        int updated = demandRepository.deductRemainMu(demandId, mu);
        if (updated == 0) {
            return false;
        }
        demandRepository.markFullIfExhausted(demandId);
        return true;
    }

    @Transactional
    public void release(Long demandId, BigDecimal mu) {
        demandRepository.findById(demandId).ifPresent(d -> {
            d.setRemainMu(d.getRemainMu().add(mu));
            if (d.getStatus() == DemandStatus.FULL && d.getRemainMu().compareTo(BigDecimal.ZERO) > 0) {
                d.setStatus(DemandStatus.OPEN);
            }
            demandRepository.save(d);
        });
    }
}
