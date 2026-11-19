package com.reapnow.demand;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 天气预警联动开关（配置来自 Nacos：reapnow.feature.weather-alert）。
 *
 * 单独抽成一个 @RefreshScope 的小 Bean，而不是直接挂在定时任务上：
 * —— @Scheduled 的 Bean 在配置刷新（销毁重建）后容易丢掉调度句柄，
 *    让任务持有这个代理、每次巡检时现取值，既能热更新又不受影响。
 *
 * 开关语义：开启后，临期加急的判定窗口从「截止前 48 小时」提前到「72 小时」
 * —— 气象台发了 48 小时降雨预警，倒伏 + 雨前抢收的需求要更早进入加急曝光。
 */
@Component
@RefreshScope
public class WeatherAlertHolder {

    @Value("${reapnow.feature.weather-alert:false}")
    private boolean weatherAlert;

    public boolean isOn() {
        return weatherAlert;
    }
}
