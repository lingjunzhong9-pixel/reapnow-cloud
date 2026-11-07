package com.reapnow.demand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.reapnow")
@EnableFeignClients(basePackages = "com.reapnow.common.client")
public class DemandApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemandApplication.class, args);
    }
}
