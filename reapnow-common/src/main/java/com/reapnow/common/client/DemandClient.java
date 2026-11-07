package com.reapnow.common.client;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.dto.DemandVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/** 需求服务客户端：order-service 抢单与取消时调用 */
@FeignClient(name = "reapnow-demand-service")
public interface DemandClient {

    @GetMapping("/api/demands/{id}")
    ApiResponse<DemandVO> byId(@PathVariable("id") Long id);

    /** 原子扣减亩数；data=false 表示被抢走了 */
    @PostMapping("/api/demands/{id}/deduct")
    ApiResponse<Boolean> deduct(@PathVariable("id") Long id, @RequestParam("mu") BigDecimal mu);

    /** 取消订单时把亩数退回 */
    @PostMapping("/api/demands/{id}/release")
    ApiResponse<Boolean> release(@PathVariable("id") Long id, @RequestParam("mu") BigDecimal mu);
}
