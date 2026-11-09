package com.reapnow.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户服务种子数据（幂等）。
 *
 * 数据设计要点：**农户要分布在不同的麦熟带**。
 * 跨区机收的本质是小麦由南往北依次成熟 —— 湖北最早，河南次之，山西、河北最晚。
 * 所以农户不是随便撒几个，而是沿着这条真实的地理线铺开，
 * 这样抢单大厅「按紧急度排序」才有意义：机手看到的顺序，正好就是麦熟推进的顺序。
 */
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

        // ---- 农户：沿麦熟带由南往北 ----
        seed("chenb",    "陈保国", "13700003333", "湖北荆州监利",   UserRole.FARMER, true);
        seed("ligf",     "李桂芳", "13800001111", "安徽阜阳颍东区", UserRole.FARMER, true);
        seed("zhangsht", "张守田", "13700004444", "安徽阜阳颍东区", UserRole.FARMER, true);
        seed("sunxy",    "孙秀英", "13700005555", "江苏徐州铜山区", UserRole.FARMER, true);
        seed("wangxl",   "王秀兰", "13800002222", "河南周口项城",   UserRole.FARMER, true);
        seed("liugh",    "刘桂花", "13700006666", "河南商丘睢阳区", UserRole.FARMER, true);
        seed("zhaodz",   "赵德柱", "13800003333", "山东菏泽曹县",   UserRole.FARMER, true);
        seed("zhouj",    "周建国", "13700007777", "河北邯郸魏县",   UserRole.FARMER, true);
        seed("guocs",    "郭长顺", "13700008888", "河北石家庄赵县", UserRole.FARMER, true);
        seed("wangyf",   "王有福", "13700009999", "山西运城盐湖区", UserRole.FARMER, true);

        // ---- 跨区机手：从陕西、河南出发，追着麦熟往北走 ----
        seed("youdg",  "尤大哥", "13900001111", "陕西渭南",     UserRole.MACHINE_OWNER, true);
        seed("liuk",   "刘康",   "13900002222", "陕西宝鸡岐山", UserRole.MACHINE_OWNER, true);
        seed("wangjj", "王建军", "13900003333", "河南南阳唐河", UserRole.MACHINE_OWNER, true);

        // ---- 后台 ----
        seed("dispatcher", "县农机服务中心", "13700001111", "安徽阜阳颍东区", UserRole.DISPATCHER, true);
        seed("admin",      "平台管理员",     "13700002222", "北京",         UserRole.ADMIN, true);

        System.out.println("[user-service] 种子用户已写入 " + userRepository.count() + " 个");
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
