package com.reapnow.common.client;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.dto.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务客户端。
 * 当前用 spring.cloud.discovery.client.simple 配置固定地址；
 * 接入 Nacos 后删掉该配置即可按服务名负载均衡。
 */
@FeignClient(name = "reapnow-user-service")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserVO> byId(@PathVariable("id") Long id);

    @GetMapping("/api/users/by-username/{username}")
    ApiResponse<UserVO> byUsername(@PathVariable("username") String username);

    /** token 换用户信息，供各服务做鉴权 */
    @GetMapping("/api/auth/verify")
    ApiResponse<UserVO> verify(@RequestParam("token") String token);
}
