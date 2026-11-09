package com.reapnow.demand;

import com.reapnow.common.client.UserClient;
import com.reapnow.common.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求服务种子数据。农户 ID 通过用户服务按用户名查询获得，
 * 不再假设「用户表在本库」—— 这是拆服务后必须改掉的一个隐含依赖。
 *
 * 数据设计要点：**按小麦成熟由南往北的顺序铺开**。
 *   湖北荆州（+1 天）→ 安徽阜阳（+2 天）→ 江苏徐州（+4 天）
 *   → 河南周口（+5 天）→ 河南商丘（+6 天）→ 山东菏泽（+8 天）→ 河北邯郸（+10 天）
 *
 * 这条线就是跨区机收的真实作业路线：机手从南方收完一路往北追。
 * 抢单大厅按「最迟收割日」升序排列，看到的顺序恰好就是麦熟推进的顺序 ——
 * 这不是为了好看，是这个行业本来的样子。
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class DemandDataSeeder implements CommandLineRunner {

    private final DemandRepository demandRepository;
    private final UserClient userClient;

    @Override
    public void run(String... args) {
        if (demandRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        // ---------------- 湖北（最早熟，最急）----------------
        seed("chenb", "陈保国", "13700003333", "江边沙壤地", "湖北荆州监利",
                "35", "75", CropStage.OVERRIPE, today.plusDays(1), false,
                "麦子已经过熟，割台一碰就掉粒。明天必须收完，晚了损失更大。");

        // ---------------- 安徽 ----------------
        seed("ligf", "李桂芳", "13800001111", "村东头三等地", "安徽阜阳颍东区",
                "62", "70", CropStage.OVERRIPE, today.plusDays(2), false,
                "麦子过熟，粒容易掉。天气预报后天有中雨，必须在雨前收完。");
        seed("zhangsht", "张守田", "13700004444", "河滩低洼地", "安徽阜阳颍东区",
                "18", "85", CropStage.OVERRIPE, today.plusDays(2), true,
                "地块倒伏严重，机器要慢一些，可以加价。后天有雨，必须收完。");

        // ---------------- 江苏 ----------------
        seed("sunxy", "孙秀英", "13700005555", "大路边地块", "江苏徐州铜山区",
                "30", "68", CropStage.RIPE, today.plusDays(4), false,
                "地在省道边上，收割机进出方便，不耽误时间。");

        // ---------------- 河南 ----------------
        seed("wangxl", "王秀兰", "13800002222", "南坡连片地", "河南周口项城",
                "120", "55", CropStage.RIPE, today.plusDays(5), false,
                "地块连片规整，120 亩一次能干完，单价可以谈。");
        seed("liugh", "刘桂花", "13700006666", "村北二等地", "河南商丘睢阳区",
                "45", "62", CropStage.RIPE, today.plusDays(6), false,
                "地不算大，但离村近，机手吃住方便。");

        // ---------------- 山东 ----------------
        seed("zhaodz", "赵德柱", "13800003333", "村西二等地", "山东菏泽曹县",
                "45", "60", CropStage.JUST_RIPE, today.plusDays(8), false,
                "刚熟，还能再等两三天，想提前把机手约好。");

        // ---------------- 河北（最晚熟）----------------
        seed("zhouj", "周建国", "13700007777", "东洼连片地", "河北邯郸魏县",
                "80", "52", CropStage.JUST_RIPE, today.plusDays(10), false,
                "麦子刚灌浆完，还有十来天。提前发出来，好让机手安排路线。");

        System.out.println("[demand-service] 种子需求已写入 " + demandRepository.count() + " 条");
    }

    private void seed(String username, String farmerName, String farmerPhone, String plotName,
                      String region, String mu, String price, CropStage stage,
                      LocalDate deadline, boolean lodged, String remark) {
        Long farmerId;
        try {
            UserVO u = userClient.byUsername(username).getData();
            farmerId = (u == null ? 1L : u.id());
        } catch (Exception e) {
            farmerId = 1L;
        }
        Demand d = new Demand();
        d.setDemandNo("DM" + System.currentTimeMillis()
                + String.format("%03d", demandRepository.count() + 1));
        d.setFarmerId(farmerId);
        d.setFarmerName(farmerName);
        d.setFarmerPhone(farmerPhone);
        d.setPlotName(plotName);
        d.setRegion(region);
        d.setMu(new BigDecimal(mu));
        d.setRemainMu(new BigDecimal(mu));
        d.setPricePerMu(new BigDecimal(price));
        d.setCropStage(stage);
        d.setDeadline(deadline);
        d.setLodged(lodged);
        d.setRemark(remark);
        d.setStatus(DemandStatus.OPEN);
        d.setCreatedAt(LocalDateTime.now().minusHours(demandRepository.count()));
        demandRepository.save(d);
    }
}
