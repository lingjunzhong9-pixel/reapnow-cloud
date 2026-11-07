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
 * 不再假设「用户表在本库」——这是拆服务后必须改掉的一个隐含依赖。
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
        seed("ligf", "李桂芳", "13800001111", "村东头三等地", "安徽阜阳颍东区",
                "62", "70", CropStage.OVERRIPE, today.plusDays(2), false,
                "麦子已经过熟，粒容易掉。天气预报后天有中雨，必须在雨前收完。");
        seed("ligf", "张守田", "13800004444", "河滩低洼地", "安徽阜阳颍东区",
                "18", "80", CropStage.OVERRIPE, today.plusDays(1), true,
                "地块倒伏严重，机器要慢一些，可以加价。明天必须收完。");
        seed("wangxl", "王秀兰", "13800002222", "南坡连片地", "河南周口项城",
                "120", "55", CropStage.RIPE, today.plusDays(3), false,
                "地块连片规整，机器好作业，单价可以谈。");
        seed("zhaodz", "赵德柱", "13800003333", "村西二等地", "山东菏泽曹县",
                "45", "60", CropStage.JUST_RIPE, today.plusDays(5), false,
                "刚熟，还可以再等两三天，想提前约好机手。");
        seed("ligf", "孙秀英", "13800005555", "大路边地块", "河南周口项城",
                "30", "65", CropStage.RIPE, today.plusDays(3), false,
                "地在大路边，机器进出方便。");
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
