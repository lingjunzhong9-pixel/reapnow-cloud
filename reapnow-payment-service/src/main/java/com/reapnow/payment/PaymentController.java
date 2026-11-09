package com.reapnow.payment;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.PaymentDetailVO;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // ==================================================================
    //  查询接口 —— 对应验收清单 A-9「定金流水查询」
    // ==================================================================

    /**
     * 按订单查定金流水。
     * 农户在「谁抢了」页、机手在「我的接单」页都需要看到这笔定金到底付没付、退没退。
     * 登录即可查（不限制角色），但只查得到这一单的流水，看不到全平台的。
     */
    @GetMapping("/by-order/{orderId}")
    public ApiResponse<List<Payment>> byOrder(@PathVariable Long orderId,
                                              @RequestHeader("X-Token") String token) {
        userClient.verify(token);   // 未登录直接 401
        return ApiResponse.ok(paymentService.listByOrder(orderId));
    }

    /**
     * 后台：全平台定金流水列表，支持按状态筛选与分页。
     *
     * <p>只有 ADMIN 与 DISPATCHER 能查 —— 流水涉及金额，不给普通农户/机手开放。
     * 角色来自网关注入后经用户服务校验的身份，前端无法伪造。
     */
    @GetMapping
    public ApiResponse<Page<Payment>> list(@RequestHeader("X-Token") String token,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        UserVO me = userClient.verify(token).getData();
        if (!"ADMIN".equals(me.role()) && !"DISPATCHER".equals(me.role())) {
            throw new BusinessException("仅管理员与调度员可查看定金流水列表");
        }
        return ApiResponse.ok(paymentService.listAll(status, page, size));
    }

    /**
     * 后台：单条流水详情 —— 含关联订单、地块与交易双方。
     *
     * <p>列表页只给流水号/金额/状态，管理员要核账必须能点进去看到：
     * 谁付的（机手）、付给谁（农户）、哪块地、订单现在什么状态。
     * 这些字段分散在支付服务、订单服务、用户服务三处，由 paymentService.detail() 聚合。
     */
    @GetMapping("/{id}")
    public ApiResponse<PaymentDetailVO> detail(@PathVariable Long id,
                                               @RequestHeader("X-Token") String token) {
        UserVO me = userClient.verify(token).getData();
        if (!"ADMIN".equals(me.role()) && !"DISPATCHER".equals(me.role())) {
            throw new BusinessException("仅管理员与调度员可查看定金流水详情");
        }
        return ApiResponse.ok(paymentService.detail(id));
    }
}
