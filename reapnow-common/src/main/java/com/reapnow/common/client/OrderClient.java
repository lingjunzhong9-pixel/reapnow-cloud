package com.reapnow.common.client;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.dto.OrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** 订单服务客户端：payment-service 支付成功后回写订单状态 */
@FeignClient(name = "reapnow-order-service")
public interface OrderClient {

    @GetMapping("/api/orders/{id}")
    ApiResponse<OrderVO> byId(@PathVariable("id") Long id);

    /** 定金支付成功，订单转 DEPOSIT_PAID */
    @PostMapping("/api/orders/{id}/mark-paid")
    ApiResponse<Boolean> markPaid(@PathVariable("id") Long id);
}
