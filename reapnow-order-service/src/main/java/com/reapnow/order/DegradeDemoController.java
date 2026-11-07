package com.reapnow.order;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.client.DemandClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 熔断演示（C2 Sentinel 保护规则接入）。
 *
 * 场景：订单服务依赖需求服务，需求服务变慢时（例如数据库慢查询、GC），
 * 如果不加熔断，订单服务的线程会全部堵在这个调用上，最终自己也被拖垮。
 *
 * 加了熔断后：统计窗口内慢调用比例超过阈值 → 熔断器打开 →
 * 后续请求直接走 fallback 降级返回，不再打下游 → 窗口过后进入半开状态试探恢复。
 *
 * 规则定义在 SentinelRuleConfig 的 initDegradeRules()，资源名 orderDegradeDemo。
 */
@RestController
@RequestMapping("/api/orders/degrade-demo")
@RequiredArgsConstructor
public class DegradeDemoController {

    private final DemandClient demandClient;

    @GetMapping("/{demandId}")
    @SentinelResource(value = "orderDegradeDemo",
            blockHandler = "degradeBlocked",
            fallback = "degradeFallback")
    public ApiResponse<Map<String, Object>> demo(@PathVariable Long demandId,
                                                 @RequestParam(defaultValue = "1200") long ms) {
        String downstream = demandClient.slow(demandId, ms).getData();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", "正常调用成功（未触发熔断）");
        m.put("downstream", downstream);
        m.put("costMs", ms);
        return ApiResponse.ok(m);
    }

    /** 被流控/熔断规则拦截时进入 —— 返回降级内容而不是异常堆栈 */
    public ApiResponse<Map<String, Object>> degradeBlocked(Long demandId, long ms, BlockException ex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", "已被 Sentinel 熔断，本次未调用下游");
        m.put("reason", "慢调用比例超阈值，熔断器打开");
        return ApiResponse.ok(m);
    }

    /** 下游抛异常时进入 —— 业务降级 */
    public ApiResponse<Map<String, Object>> degradeFallback(Long demandId, long ms, Throwable t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", "调用异常，已降级");
        m.put("reason", t.getMessage());
        return ApiResponse.ok(m);
    }
}
