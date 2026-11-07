package com.reapnow.demand;

public enum CropStage {
    JUST_RIPE,   // 刚熟，还可以再等两三天
    RIPE,        // 适收，最佳收割窗口
    OVERRIPE     // 过熟，麦粒易爆开脱落，必须尽快收
}
