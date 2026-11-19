package com.reapnow.demand;

import com.reapnow.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置中心演示接口。
 * @RefreshScope 让这个 Bean 在 Nacos 配置变更并刷新后重新注入属性值，
 * 不重启服务即可看到新值 —— 这是配置中心相对本地配置文件的关键价值。
 *
 * 注意 weather-alert 这个键的坑：
 *   它同时被 {@link WeatherAlertHolder} 以 boolean 读取（决定加急窗口 48h / 72h）。
 *   曾经在 Nacos 里把它填成一句中文提示，导致 demand-service 启动直接失败
 *   （Invalid boolean value）。所以这里**必须也按 boolean 读**，
 *   人类可读的提示文案另开一个键 weather-alert-text，不能混用同一个键。
 */
@RestController
@RequestMapping("/api/config")
@RefreshScope
public class ConfigController {

    @Value("${reapnow.banner:未配置}")
    private String banner;

    @Value("${reapnow.deposit-rate:0.3}")
    private String depositRate;

    /** 开关：true = 加急窗口提前到 72 小时 */
    @Value("${reapnow.feature.weather-alert:false}")
    private boolean weatherAlert;

    /** 展示用文案，与开关分开，避免类型冲突 */
    @Value("${reapnow.feature.weather-alert-text:未配置预警文案}")
    private String weatherAlertText;

    @GetMapping("/demo")
    public ApiResponse<Map<String, String>> demo() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("source", "Nacos 配置中心 · dataId=reapnow-demand-service.yaml");
        m.put("banner", banner);
        m.put("depositRate", depositRate);
        m.put("weatherAlert", String.valueOf(weatherAlert));
        m.put("weatherAlertText", weatherAlertText);
        m.put("urgentWindowHours", weatherAlert ? "72" : "48");
        return ApiResponse.ok(m);
    }
}
