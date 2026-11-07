package com.reapnow.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用户服务种子数据（幂等） */
@Component
@Order(1)
@RequiredArgsConstructor
public class UserDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        seed("ligf", "李桂芳", "13800001111", "安徽阜阳颍东区", UserRole.FARMER, true);
        seed("wangxl", "王秀兰", "13800002222", "河南周口项城", UserRole.FARMER, true);
        seed("zhaodz", "赵德柱", "13800003333", "山东菏泽曹县", UserRole.FARMER, true);
        seed("youdg", "尤大哥", "13900001111", "陕西渭南", UserRole.MACHINE_OWNER, true);
        seed("liuk", "刘康", "13900002222", "陕西宝鸡岐山", UserRole.MACHINE_OWNER, true);
        seed("dispatcher", "县农机服务中心", "13700001111", "安徽阜阳颍东区", UserRole.DISPATCHER, true);
        seed("admin", "平台管理员", "13700002222", "北京", UserRole.ADMIN, true);
    }

    private void seed(String username, String nickname, String phone, String region,
                      UserRole role, boolean verified) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("123456");
        u.setNickname(nickname);
        u.setPhone(phone);
        u.setRegion(region);
        u.setRole(role);
        u.setCreditScore(new BigDecimal("100.0"));
        u.setVerified(verified);
        u.setCreatedAt(LocalDateTime.now());
        userRepository.save(u);
    }
}
