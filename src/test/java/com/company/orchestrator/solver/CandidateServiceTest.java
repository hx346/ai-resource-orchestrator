package com.company.orchestrator.solver;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.company.orchestrator.solver.PlanningData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 候选过滤的容量数学（单位：内部基点，10000 = 100%）：
 * 技能门槛、活跃状态、分配比例、窗口清零、占用扣减、PREFERRED 评分、周末剔除。
 * Candidate math in basis points (10000 = 100%): skill gate, activity,
 * allocation ratio, window zeroing, booking subtraction, preferred scoring, weekends.
 */
class CandidateServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 5); // 周一 / a Monday

    private static Work task(long id, int hours, List<Need> needs) {
        return new Work(id, "t" + id, MON, MON.plusDays(4), hours, 3, needs);
    }

    private final CandidateService service = new CandidateService();

    @Test
    void requiredSkillBelowLevelFiltersOut() {
        var input = new Input(1, List.of(task(1, 40, List.of(new Need(9L, 3, 10000, "REQUIRED")))),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of(9L, 2))), List.of(), List.of(), "h");
        assertTrue(service.candidates(input, input.tasks().getFirst()).isEmpty());
    }

    @Test
    void nonActiveEmployeeIsSkipped() {
        var input = new Input(1, List.of(task(1, 40, List.of())),
                List.of(new Person(1, "p1", 40, 8000, "ON_LEAVE", Map.of())), List.of(), List.of(), "h");
        assertTrue(service.candidates(input, input.tasks().getFirst()).isEmpty());
    }

    @Test
    void allocationIsBpsOfDailyCapacityAndCapacityGateApplies() {
        // 40h 铺满 5 个工作日 × 40h/周 → 需要 10000bps（100%），80% 容量的人不达标
        // 40h over 5 workdays at 40h/week needs 10000 bps; an 80%-capacity person fails
        var tight = new Input(1, List.of(task(1, 40, List.of())),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of())), List.of(), List.of(), "h");
        assertTrue(service.candidates(tight, tight.tasks().getFirst()).isEmpty());

        // 同样任务给 50h/周 → 8000bps，80% 容量可容纳 / same task at 50h/week → 8000 bps, fits
        var fits = new Input(1, List.of(task(1, 40, List.of())),
                List.of(new Person(1, "p1", 50, 8000, "ACTIVE", Map.of())), List.of(), List.of(), "h");
        var candidates = service.candidates(fits, fits.tasks().getFirst());
        assertEquals(1, candidates.size());
        assertEquals(8000, candidates.getFirst().allocation());
    }

    @Test
    void nonAvailableWindowZeroesCapacity() {
        var input = new Input(1, List.of(task(1, 20, List.of())),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of())),
                List.of(new Window(1, MON, MON.plusDays(4), 5000, "ON_LEAVE")), List.of(), "h");
        assertTrue(service.candidates(input, input.tasks().getFirst()).isEmpty());
    }

    @Test
    void existingBookingSubtractsFromRemaining() {
        // 40h → 10000bps，容量 8000 已被占用 5000 → 余 3000，不满足
        var input = new Input(1, List.of(task(1, 40, List.of())),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of())),
                List.of(), List.of(new Booking(1, MON, MON.plusDays(4), 5000)), "h");
        assertTrue(service.candidates(input, input.tasks().getFirst()).isEmpty());
    }

    @Test
    void preferredSkillScoreIsWeightedRatio() {
        // PREFERRED 不做门槛只影响评分：L1 对 minLevel=5 → 0.2 → 20 分
        // PREFERRED never gates, only scores: level 1 vs minLevel 5 → 0.2 → 20
        var input = new Input(1, List.of(task(1, 8, List.of(new Need(9L, 5, 10000, "PREFERRED")))),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of(9L, 1))), List.of(), List.of(), "h");
        var candidates = service.candidates(input, input.tasks().getFirst());
        assertEquals(1, candidates.size());
        assertEquals(20, candidates.getFirst().score());
    }

    @Test
    void weekendDaysAreExcludedFromCapacityMath() {
        // 8h 任务落在周五→周日，仅 1 个工作日 → 需要 10000bps，80% 容量不满足
        // An 8h task spanning Fri–Sun has one workday → needs 10000 bps, 80% fails
        var input = new Input(1, List.of(new Work(1, "t1", MON.plusDays(4), MON.plusDays(6), 8, 3, List.of())),
                List.of(new Person(1, "p1", 40, 8000, "ACTIVE", Map.of())), List.of(), List.of(), "h");
        assertTrue(service.candidates(input, input.tasks().getFirst()).isEmpty());
    }
}
