package com.reapnow.common.config;

import com.reapnow.common.BusinessException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 把下游服务返回的错误转换成 BusinessException，
 * 这样上游可以沿用同一套错误码与中文提示，不用层层翻译。
 */
@Configuration
public class FeignErrorConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            String msg = "远程服务调用失败";
            try {
                if (response.body() != null) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(response.body().asInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    String body = sb.toString();
                    int i = body.indexOf("message");
                    if (i >= 0) {
                        int c = body.indexOf(':', i);
                        if (c > 0) {
                            int q1 = body.indexOf('"', c);
                            int q2 = q1 >= 0 ? body.indexOf('"', q1 + 1) : -1;
                            if (q1 > 0 && q2 > q1) {
                                msg = body.substring(q1 + 1, q2);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // 解析失败就用默认提示
            }
            return new BusinessException(status == 401 ? 401 : 400, msg);
        };
    }
}
