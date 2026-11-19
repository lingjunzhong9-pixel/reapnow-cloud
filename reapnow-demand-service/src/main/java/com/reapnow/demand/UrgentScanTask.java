package com.reapnow.demand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 临期兜底巡检任务 —— 回答「农户发了需求只能干等怎么办」的机制层。
 *
 * 每 30 分钟扫描一次所有 OPEN 状态的需求，做三件事：
 *   1. 统一初始化历史数据的 urgent 空值（幂等）；
 *   2. 临期打标：截止前 48 小时（天气预警开启时 72 小时）仍未被抢满 → urgent = true，
 *      抢单大厅默认排序把这类单顶到最前，调度工作台也会看到；
 *   3. 授权加价：截止前 1 天（daysLeft &lt;= 1）仍无人接满、且农户发布时授权了加价上限 →
 *      按授权幅度一次性上调期望单价重新曝光（markupApplied 保证只加一次）。
 *
 *      为什么是提前 1 天而不是截止当天：等到 daysLeft=0 才加价，机手几乎没有
 *      调度空间了，加价也就失去了意义 —— 老师那句「别等庄稼掉落」要的就是这个提前量。
 *
 * 另暴露 manualScan() 供调度员工作台手动触发（答辩演示用，不用等 30 分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgentScanTask {

    private final DemandRepository demandRepository;
    private final WeatherAlertHolder weatherAlertHolder;

    /** 常规加急窗口：截止前 48 小时 */
    private static final long URGENT_DAYS = 2;
    /** 天气预警联动后的加急窗口：截止前 72 小时 */
    private static final long URGENT_DAYS_WEATHER = 3;

    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 20 * 1000)
    @Transactional
    public void scheduledScan() {
        Map<String, Object> r = scan();
        log.info("临期巡检完成：{}（扫描 {} 条开放需求）", r.get("summary"), r.get("scanned"));
    }

    /** 手动触发入口：调度员工作台 / 答辩演示用 */
    @Transactional
    public Map<String, Object> manualScan() {
        return scan();
    }

    protected Map<String, Object> scan() {
        demandRepository.initUrgentNull();

        boolean weather = weatherAlertHolder.isOn();
        long urgentDays = weather ? URGENT_DAYS_WEATHER : URGENT_DAYS;

        List<Demand> opens = demandRepository.findByStatus(DemandStatus.OPEN);
        int marked = 0, bumped = 0;

        for (Demand d : opens) {
            boolean changed = false;
            long daysLeft = d.getDaysLeft();

            // 1) 临期打标
            if (!Boolean.TRUE.equals(d.getUrgent()) && daysLeft <= urgentDays) {
                d.setUrgent(true);
                marked++;
                changed = true;
            }

            // 2) 授权加价：截止前 1 天、农户已授权、且还没加过价
            //    提前到 daysLeft<=1 而不是 ==0 —— 越早在还有调度空间时加价，才可能被接走
            if (daysLeft <= 1 && d.getPricePerMu() != null
                    && d.getMarkupLimit() != null && d.getMarkupLimit() > 0
                    && !Boolean.TRUE.equals(d.getMarkupApplied())) {
                BigDecimal factor = BigDecimal.ONE.add(
                        BigDecimal.valueOf(d.getMarkupLimit()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                d.setPricePerMu(d.getPricePerMu().multiply(factor).setScale(2, RoundingMode.HALF_UP));
                d.setMarkupApplied(true);
                bumped++;
                changed = true;
            }

            if (changed) {
                demandRepository.save(d);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", opens.size());
        result.put("urgentMarked", marked);
        result.put("priceBumped", bumped);
        result.put("weatherAlertOn", weather);
        result.put("urgentWindowHours", weather ? 72 : 48);
        result.put("summary", "新标记加急 " + marked + " 单，授权加价 " + bumped + " 单"
                + (weather ? "（天气预警联动：72 小时窗口）" : ""));
        return result;
    }
}
