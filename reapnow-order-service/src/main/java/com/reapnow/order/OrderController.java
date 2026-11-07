package com.reapnow.order;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
