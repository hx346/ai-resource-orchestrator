package com.company.orchestrator.capability;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.orchestrator.capability.WeeklySupplyModel.Demand;
import com.company.orchestrator.capability.WeeklySupplyModel.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 周级供给模型：峰值封顶、盈余不跨周弥补、缺口人数折算 / weekly model: peak capping and gap-people math. */
class WeeklySupplyModelTest {

    private static final List<String> TWO_WEEKS = List.of("2026-10-05", "2026-10-12");

    @Test
    void surplusWeeksCannotCoverShortageWeeks() {
        // 两周共 80h 需求、共 80h 供给，但第 1 周缺 40h、第 2 周多 40h → 平铺无缺口、周级缺口 40h
        var demand = new Demand(1, "Java", 3, 2, new double[]{80, 0});
        var supplier = List.of(new Supplier(10, new double[]{40, 40}));
        var row = WeeklySupplyModel.row(TWO_WEEKS, 10, demand, supplier, false);
        assertEquals(80L, row.get("demandHours"));
        assertEquals(40L, row.get("availableHours"));   // 第 1 周 40 有效，第 2 周需求 0 封顶
        assertEquals(40L, row.get("gapHours"));
        assertEquals(2, ((List<?>) row.get("weekly")).size());
    }

    @Test
    void gapPeopleUsesEffectiveUtilization() {
        // 10 个工作日、缺口 64h → 64 / (10×8×0.8) = 1 人
        var demand = new Demand(1, "Java", 3, 1, new double[]{64, 0});
        var row = WeeklySupplyModel.row(TWO_WEEKS, 10, demand, List.of(), true);
        assertEquals(1, row.get("gapPeople"));
        // 缺口 65h → 向上取整 2 人
        var slightly = new Demand(1, "Java", 3, 1, new double[]{65, 0});
        assertEquals(2, WeeklySupplyModel.row(TWO_WEEKS, 10, slightly, List.of(), true).get("gapPeople"));
    }

    @Test
    void supplierShortArraysAndNegativeSupplyAreClamped() {
        // 供给数组比周数短、且出现负值（容量被全额占用）时按 0 处理
        var demand = new Demand(1, "Java", 3, 1, new double[]{20, 20});
        var supplier = List.of(new Supplier(10, new double[]{-5}));
        var row = WeeklySupplyModel.row(TWO_WEEKS, 10, demand, supplier, true);
        assertEquals(0L, row.get("availableHours"));
        assertEquals(40L, row.get("gapHours"));
    }

    @Test
    void compactModeOmitsWeeklyBreakdown() {
        var demand = new Demand(1, "Java", 3, 1, new double[]{10, 10});
        var row = WeeklySupplyModel.row(TWO_WEEKS, 10, demand, List.of(new Supplier(10, new double[]{10, 10})), true);
        assertEquals(0, row.get("gapPeople"));
        assertEquals(null, row.get("weekly"));
    }
}
