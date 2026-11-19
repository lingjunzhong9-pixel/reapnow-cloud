package com.reapnow.user;

import com.reapnow.common.ApiResponse;
import com.reapnow.common.BusinessException;
import com.reapnow.common.dto.UserBriefVO;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询接口。
 *
 * 两类用途要区分开：
 *   1. {@code /{id}}、{@code /by-username/{username}} —— **内部接口**，
 *      供其他服务通过 Feign 直连调用（不走网关、不校验 token），返回完整 UserVO；
 *   2. {@code GET /api/users} —— **对外接口**，给调度工作台筛机手用，
 *      必须校验角色（ADMIN / DISPATCHER），且**不返回手机号**。
 *
 * 历史缺陷：list() 之前没有任何角色校验，网关只要求「带任意合法 token」，
 * 结果任何登录用户（包括农户）都能拉到全量用户列表和手机号。这里已修正。
 *
 * 注意：本服务自己验 token 用 {@link TokenStore}（JWT 本地验签），
 * 不能注入 UserClient —— 那是给别的服务调本服务用的，自调用会形成循环依赖，
 * 启动时会报「required a bean of type UserClient that could not be found」。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final TokenStore tokenStore;

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

    /**
     * 全量用户列表（脱敏）—— 仅 ADMIN / DISPATCHER。
     * 调度工作台用它筛「已认证机手」，因此不需要手机号，只返回指派需要的字段。
     */
    @GetMapping
    public ApiResponse<List<UserBriefVO>> list(@RequestHeader("X-Token") String token) {
        User me = userRepository.findById(tokenStore.require(token))
                .orElseThrow(() -> new BusinessException("登录已失效，请重新登录"));
        if (me.getRole() != UserRole.ADMIN && me.getRole() != UserRole.DISPATCHER) {
            // 403 = 已登录但权限不足（区别于 401 未登录），前端可据此提示而不是踢回登录页
            throw new BusinessException(403, "仅管理员与调度员可查看用户列表");
        }
        return ApiResponse.ok(userRepository.findAll().stream().map(this::toBrief).toList());
    }

    private UserVO toVO(User u) {
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getPhone(),
                u.getRegion(), u.getRole() == null ? null : u.getRole().name(),
                u.getCreditScore(), u.getVerified());
    }

    /** 脱敏：不含手机号 */
    private UserBriefVO toBrief(User u) {
        return new UserBriefVO(u.getId(), u.getUsername(), u.getNickname(), u.getRegion(),
                u.getRole() == null ? null : u.getRole().name(),
                u.getCreditScore(), u.getVerified());
    }
}
