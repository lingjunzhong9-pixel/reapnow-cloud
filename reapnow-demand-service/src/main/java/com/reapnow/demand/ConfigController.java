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
 */
@RestController
@RequestMapping("/api/config")
@RefreshScope
public class ConfigController {

    @Value("${reapnow.banner:未配置}")
    private String banner;

    @Value("${reapnow.deposit-rate:0.3}")
    private String depositRate;

    @Value("${reapnow.feature.weather-alert:false}")
    private String weatherAlert;

    @GetMapping("/demo")
    public ApiResponse<Map<String, String>> demo() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("source", "Nacos 配置中心 · dataId=reapnow-demand-service.yaml");
        m.put("banner", banner);
        m.put("depositRate", depositRate);
        m.put("weatherAlert", weatherAlert);
        return ApiResponse.ok(m);
    }
}
