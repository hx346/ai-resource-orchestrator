package com.company.orchestrator.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按周精化供给模型（Phase 6 余项）：需求按任务工作日均摊到周，供给按
 * “达标员工周容量 − 生效分配占用 − 不可用天数扣减”逐周计算，并对需求封顶后汇总——
 * 同周盈余不能弥补他周缺口，因此能捕捉平铺模型看不到的峰值短缺。
 * Weekly-refined supply model: demand is spread over each task's workdays;
 * supply is each qualified person's weekly capacity minus bookings minus
 * blackout days, capped by weekly demand before summing — surplus weeks cannot
 * cover shortage weeks, so peak shortages surface that the flat model misses.
 */
public final class WeeklySupplyModel {

    private WeeklySupplyModel() {}

    /** 与平铺口径一致的有效投入系数 / same effective-utilization factor as the flat model. */
    public static final double EFFECTIVE = 0.8;

    /** 按技能的周需求 / per-skill weekly demand aligned with the week list. */
    public record Demand(long skillId, String skillName, int requiredLevel, int taskCount, double[] demandByWeek) {}

    /** 一名达标员工的周净供给工时 / one qualified person's net weekly supply hours. */
    public record Supplier(long employeeId, double[] supplyByWeek) {}

    /** 汇总一行：总量 + 逐周明细（compact 时省略 weekly 数组）/ one aggregated row, weekly breakdown optional. */
    public static Map<String, Object> row(List<String> weeks, int windowWorkdays, Demand demand, List<Supplier> suppliers, boolean compact) {
        int n = weeks.size();
        var supplyByWeek = new double[n];
        for (var supplier : suppliers)
            for (int i = 0; i < n; i++)
                supplyByWeek[i] += i < supplier.supplyByWeek().length ? Math.max(0, supplier.supplyByWeek()[i]) : 0;
        double totalDemand = 0, available = 0, gap = 0;
        var weekly = new ArrayList<Map<String, Object>>(n);
        for (int i = 0; i < n; i++) {
            double weekDemand = i < demand.demandByWeek().length ? Math.max(0, demand.demandByWeek()[i]) : 0;
            double supply = supplyByWeek[i];
            double effective = Math.min(weekDemand, supply);
            double weekGap = Math.max(0, weekDemand - supply);
            totalDemand += weekDemand;
            available += effective;
            gap += weekGap;
            if (!compact) weekly.add(Map.of("week", weeks.get(i), "demandHours", Math.round(weekDemand),
                    "supplyHours", Math.round(supply), "gapHours", Math.round(weekGap)));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("skillId", demand.skillId());
        result.put("skillName", demand.skillName());
        result.put("requiredLevel", demand.requiredLevel());
        result.put("taskCount", demand.taskCount());
        result.put("qualifiedCount", suppliers.size());
        result.put("demandHours", Math.round(totalDemand));
        result.put("availableHours", Math.round(available));
        result.put("gapHours", Math.round(gap));
        result.put("gapPeople", gap > 0 ? (int) Math.ceil(gap / (windowWorkdays * 8 * EFFECTIVE)) : 0);
        if (!compact) result.put("weekly", weekly);
        return result;
    }
}
