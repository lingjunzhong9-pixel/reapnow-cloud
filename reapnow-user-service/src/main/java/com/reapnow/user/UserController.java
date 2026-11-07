package com.reapnow.user;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户查询接口：供其他服务拉取用户信息 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public ApiResponse<UserVO> byId(@PathVariable Long id) {
        return ApiResponse.ok(toVO(userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"))));
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<UserVO> byUsername(@PathVariable String username) {
        return ApiResponse.ok(toVO(userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"))));
    }

    @GetMapping
    public ApiResponse<List<UserVO>> list() {
        return ApiResponse.ok(userRepository.findAll().stream().map(this::toVO).toList());
    }

    private UserVO toVO(User u) {
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getPhone(),
                u.getRegion(), u.getRole() == null ? null : u.getRole().name(),
                u.getCreditScore(), u.getVerified());
    }
}
