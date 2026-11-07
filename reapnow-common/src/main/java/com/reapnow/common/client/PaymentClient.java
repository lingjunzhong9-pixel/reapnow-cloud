package com.reapnow.common.client;

import com.reapnow.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** 支付服务客户端：order-service 取消已付定金的订单时发起退款 */
@FeignClient(name = "reapnow-payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments/{orderId}/refund")
    ApiResponse<Boolean> refund(@PathVariable("orderId") Long orderId);
}
