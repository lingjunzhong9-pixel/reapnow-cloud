package com.reapnow.payment;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserClient userClient;

    @PostMapping("/{orderId}/pay-deposit")
    public ApiResponse<Payment> payDeposit(@PathVariable Long orderId,
                                           @RequestHeader("X-Token") String token,
                                           @RequestParam(defaultValue = "MOCK") String channel,
                                           @RequestParam(defaultValue = "false") boolean simulateFail) {
        UserVO me = userClient.verify(token).getData();
        return ApiResponse.ok(paymentService.payDeposit(me.id(), orderId, channel, simulateFail));
    }

    /** 内部接口：订单取消时由订单服务发起退款 */
    @PostMapping("/{orderId}/refund")
    public ApiResponse<Boolean> refund(@PathVariable Long orderId) {
        return ApiResponse.ok(paymentService.refund(orderId));
    }
}
