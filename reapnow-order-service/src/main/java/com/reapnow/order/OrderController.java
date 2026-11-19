package com.reapnow.order;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserClient userClient;

    @PostMapping("/grab")
    public ApiResponse<HarvestOrder> grab(@RequestHeader("X-Token") String token,
                                          @Valid @RequestBody GrabRequest req) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(orderService.grab(me.id(), req));
    }

    /**
     * C03-6 行情参考价（免登录）。
     * 聚合近 30 天已成交订单单价，返回中位数与四分位：
     * 发布表单给农户当定价锚点，抢单大厅给机手打「高于行情」标签。
     */
    @GetMapping("/market-price")
    public ApiResponse<Map<String, Object>> marketPrice(
            @RequestParam(required = false) String region) {
        return ApiResponse.ok(orderService.marketPrice(region));
    }

    /**
     * C03-7 调度员兜底指派。
     * 需求临期无人抢时，调度员点名已认证机手接单 —— 与抢单共用扣减/建单/定金流程。
     */
    @PostMapping("/assign")
    public ApiResponse<HarvestOrder> assign(@RequestHeader("X-Token") String token,
                                            @Valid @RequestBody AssignRequest req) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(orderService.assign(me.id(), req));
    }

    @GetMapping("/mine")
    public ApiResponse<List<HarvestOrder>> mine(@RequestHeader("X-Token") String token) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(orderService.mine(me.id()));
    }

    @GetMapping("/by-demand/{demandId}")
    public ApiResponse<List<HarvestOrder>> byDemand(@PathVariable Long demandId) {
        return ApiResponse.ok(orderService.byDemand(demandId));
    }

    @GetMapping("/{id}")
    public ApiResponse<HarvestOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getById(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<HarvestOrder> cancel(@RequestHeader("X-Token") String token,
                                            @PathVariable Long id) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(orderService.cancel(me.id(), id));
    }

    /** 内部接口：支付服务支付成功后回调，订单转 DEPOSIT_PAID */
    @PostMapping("/{id}/mark-paid")
    public ApiResponse<Boolean> markPaid(@PathVariable Long id) {
        return ApiResponse.ok(orderService.markPaid(id));
    }
}
