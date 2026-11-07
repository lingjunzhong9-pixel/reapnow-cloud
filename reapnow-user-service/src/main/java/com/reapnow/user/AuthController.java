package com.reapnow.user;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.dto.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String nickname;
        private String phone;
        private String region;
        private String role = "FARMER";
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new BusinessException("该用户名已被注册");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(req.getPassword());
        u.setNickname(req.getNickname() == null ? req.getUsername() : req.getNickname());
        u.setPhone(req.getPhone());
        u.setRegion(req.getRegion());
        u.setRole(UserRole.valueOf(req.getRole() == null ? "FARMER" : req.getRole().toUpperCase()));
        u.setCreditScore(new BigDecimal("100.0"));
        u.setVerified(false);
        u.setCreatedAt(java.time.LocalDateTime.now());
        userRepository.save(u);
        return ApiResponse.ok(new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getPhone(),
                u.getRegion(), u.getRole().name(), u.getCreditScore(), u.getVerified()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        User u = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!u.getPassword().equals(req.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = tokenStore.issue(u.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getPhone(),
                u.getRegion(), u.getRole().name(), u.getCreditScore(), u.getVerified()));
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        tokenStore.revoke(token);
        return ApiResponse.ok();
    }

    /** 供其他服务鉴权：token 换用户信息 */
    @GetMapping("/verify")
    public ApiResponse<UserVO> verify(@RequestParam("token") String token) {
        Long uid = tokenStore.resolve(token);
        if (uid == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        User u = userRepository.findById(uid)
                .orElseThrow(() -> new BusinessException(401, "登录用户不存在"));
        return ApiResponse.ok(new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getPhone(),
                u.getRegion(), u.getRole().name(), u.getCreditScore(), u.getVerified()));
    }
}
