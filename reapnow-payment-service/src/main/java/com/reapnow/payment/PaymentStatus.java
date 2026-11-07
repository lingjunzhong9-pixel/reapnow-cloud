package com.reapnow.payment;

public enum PaymentStatus {
    PENDING,   // 待支付
    SUCCESS,   // 支付成功
    FAILED,    // 支付失败
    REFUNDED   // 已退款
}
