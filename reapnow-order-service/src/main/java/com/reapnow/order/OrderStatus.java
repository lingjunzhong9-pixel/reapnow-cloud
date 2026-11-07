package com.reapnow.order;

public enum OrderStatus {
    PENDING_DEPOSIT,  // 已抢到，待付定金（超时未付会释放亩数）
    DEPOSIT_PAID,     // 定金已付，履约中
    DONE,             // 作业完成，已结算
    CANCELLED         // 已取消，亩数已释放
}
