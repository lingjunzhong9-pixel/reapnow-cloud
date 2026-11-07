package com.reapnow.payment;

public enum PaymentType {
    DEPOSIT,      // 定金（抢单后支付，用于约束履约）
    SETTLEMENT    // 尾款结算（作业完成后支付）
}
