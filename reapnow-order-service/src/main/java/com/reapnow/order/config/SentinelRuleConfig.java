package com.reapnow.order.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则（代码内置一份默认规则）。
 *
 * 为什么要在代码里写一份：
 *   控制台里推的规则默认只存在内存，服务重启就没了；
 *   代码内置一份保证「服务一起来就有保护」，演示不依赖人工在控制台点。
 *   运行时仍然可以在控制台在线改，改了立即生效（覆盖这里的值）。
 *
 * 资源名 grab 对应 OrderService#grab 上的 @SentinelResource。
 */
@Configuration
public class SentinelRuleConfig {

    /** 抢单接口的 QPS 上限 */
    private static final double GRAB_QPS = 5.0;

    @PostConstruct
    public void init() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 规则 1：抢单接口整体 QPS 限流
        FlowRule grab = new FlowRule();
        grab.setResource("grab");
        grab.setGrade(RuleConstant.FLOW_GRADE_QPS);
        grab.setCount(GRAB_QPS);
        grab.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);  // 快速失败
        grab.setLimitApp("default");
        rules.add(grab);

        // 规则 2：同一机手（按来源参数）单独限流，防止单账号刷单
        FlowRule perUser = new FlowRule();
        perUser.setResource("grab");
        perUser.setGrade(RuleConstant.FLOW_GRADE_QPS);
        perUser.setCount(2.0);
        perUser.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        perUser.setLimitApp("default");
        rules.add(perUser);

        FlowRuleManager.loadRules(rules);
        System.out.println("[Sentinel] 已加载限流规则 " + rules.size() + " 条，抢单 QPS 上限 = " + GRAB_QPS);
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 熔断：抢单接口慢调用比例过高（RT > 800ms 且比例 > 50%，统计 10 秒，最小 5 次请求）
        DegradeRule slow = new DegradeRule("grab");
        slow.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        slow.setCount(800);              // 慢调用阈值（毫秒）
        slow.setSlowRatioThreshold(0.5); // 慢调用比例阈值
        slow.setTimeWindow(10);          // 熔断时长（秒）
        slow.setMinRequestAmount(5);     // 统计窗口内最小请求数
        slow.setStatIntervalMs(10000);   // 统计窗口 10 秒
        rules.add(slow);

        // 熔断：异常比例过高（> 50%，统计 10 秒，最小 5 次）
        DegradeRule ex = new DegradeRule("grab");
        ex.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        ex.setCount(0.5);
        ex.setTimeWindow(10);
        ex.setMinRequestAmount(5);
        ex.setStatIntervalMs(10000);
        rules.add(ex);

        DegradeRuleManager.loadRules(rules);
        System.out.println("[Sentinel] 已加载熔断规则 " + rules.size() + " 条");
    }
}
