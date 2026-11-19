package com.reapnow.demand;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demands")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;
    private final UserClient userClient;
    private final UrgentScanTask urgentScanTask;

    @PostMapping
    public ApiResponse<Demand> create(@RequestHeader("X-Token") String token,
                                      @Valid @RequestBody DemandRequest req) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(demandService.create(me.id(), req));
    }

    @GetMapping
    public ApiResponse<Page<Demand>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean urgent,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(demandService.search(status, region, keyword, sort, urgent, page, size));
    }

    /**
     * 手动触发临期巡检（仅调度员 / 管理员）。
     * 定时任务 30 分钟跑一次；调度员在工作台点一下立刻跑，答辩演示也用它。
     */
    @PostMapping("/scan-urgent")
    public ApiResponse<Map<String, Object>> scanUrgent(@RequestHeader("X-Token") String token) {
        UserVO me = userClient.verify(token).getData();
        if (!"DISPATCHER".equals(me.role()) && !"ADMIN".equals(me.role())) {
            throw new BusinessException("只有调度员可以手动触发巡检");
        }
        return ApiResponse.ok(urgentScanTask.manualScan());
    }

    @GetMapping("/mine")
    public ApiResponse<List<Demand>> mine(@RequestHeader("X-Token") String token) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(demandService.mine(me.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Demand> detail(@PathVariable Long id) {
        return ApiResponse.ok(demandService.getById(id));
    }

    /**
     * 演示接口：模拟需求服务变慢（依赖故障）。
     * 供订单服务的熔断演示调用 —— 当下游响应时间超过熔断阈值，
     * 调用方会在统计窗口内打开熔断器，后续请求直接走降级，不再打到本接口。
     */
    @GetMapping("/{id}/slow")
    public ApiResponse<String> slow(@PathVariable Long id,
                                    @RequestParam(defaultValue = "1200") long ms) {
        Demand d = demandService.getById(id);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ApiResponse.ok("慢响应完成：" + d.getPlotName() + "，耗时 " + ms + "ms");
    }

    /** 内部接口：供订单服务抢单时原子扣减亩数 */
    @PostMapping("/{id}/deduct")
    public ApiResponse<Boolean> deduct(@PathVariable Long id, @RequestParam BigDecimal mu) {
        return ApiResponse.ok(demandService.tryDeduct(id, mu));
    }

    /** 内部接口：订单取消时退回亩数 */
    @PostMapping("/{id}/release")
    public ApiResponse<Boolean> release(@PathVariable Long id, @RequestParam BigDecimal mu) {
        demandService.release(id, mu);
        return ApiResponse.ok(true);
    }
}
