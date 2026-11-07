package com.reapnow.order;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.client.DemandClient;
import com.reapnow.common.dto.DemandVO;
import lombok.RequiredArgsConstructor;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分布式事务演示（故障注入）。
 *
 * 这是给答辩演示用的接口，不是业务功能：
 *   1) 先跨服务把需求亩数扣掉（demand-service 真实写库）
 *   2) 然后故意抛异常
 *   3) 观察亩数是否被 Seata 自动回滚
 *
 * 如果没有全局事务，第 1 步扣掉的亩数会永久丢失 —— 这就是「亩数扣了但订单没生成」的经典事故。
 * 加了 @GlobalTransactional 之后，TC 会驱动 demand-service 用 undo_log 把数据改回去。
 */
@RestController
@RequestMapping("/api/orders/tx-demo")
@RequiredArgsConstructor
public class TxDemoController {

    private final DemandClient demandClient;

    @GetMapping("/{demandId}")
    @GlobalTransactional(name = "reapnow-tx-demo", rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> demo(@PathVariable Long demandId,
                                                 @RequestParam BigDecimal mu) {
        DemandVO before = demandClient.byId(demandId).getData();
        if (before == null) {
            throw new BusinessException("需求不存在");
        }

        // 第一步：跨服务真实扣减亩数
        Boolean ok = demandClient.deduct(demandId, mu).getData();
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessException("扣减失败，可能亩数不足");
        }
        DemandVO afterDeduct = demandClient.byId(demandId).getData();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demandId", demandId);
        m.put("muBefore", before.remainMu());
        m.put("muAfterDeduct", afterDeduct.remainMu());
        m.put("note", "扣减成功，现在故意抛异常，观察亩数是否被 Seata 回滚");

        System.out.println("[TX-DEMO] 扣减后剩余亩数 = " + afterDeduct.remainMu() + "，即将抛异常触发回滚");

        // 第二步：故意失败，触发全局回滚
        throw new BusinessException("【故障注入】扣亩数之后故意失败，即将触发 Seata 全局回滚");
    }
}
