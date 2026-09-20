package com.company.orchestrator.solver;
import java.time.LocalDate;
import java.util.*;
/**
 * 单位约定：数据库存百分数（NUMERIC，80.00 = 80%），内存统一基点（int，10000 = 100%）；
 * 出入库经 movePointRight(2) / BigDecimal.valueOf(x,2) 转换（员工容量、窗口容量、占用投入同口径）。
 * Unit convention: percent in the DB (80.00), basis points in memory (10000 = 100%),
 * converted at every persistence boundary.
 */
public final class PlanningData {
    private PlanningData() {}
    public record Person(long id,String name,int weeklyHours,int capacity,String status,Map<Long,Integer> skills) {}
    public record Need(long skillId,int minLevel,int weight,String type) {}
    public record Work(long id,String name,LocalDate start,LocalDate end,int hours,int priority,List<Need> needs) {}
    public record Window(long employeeId,LocalDate start,LocalDate end,int capacity,String type) {}
    public record Booking(long employeeId,LocalDate start,LocalDate end,int capacity) {}
    public record Input(long projectId,List<Work> tasks,List<Person> people,List<Window> windows,List<Booking> bookings,String hash) {}
    public record Candidate(long employeeId,String name,int score,int allocation,Map<LocalDate,Integer> remaining) {}
    public static List<LocalDate> days(LocalDate start,LocalDate end) {
        return start.datesUntil(end.plusDays(1)).filter(d -> d.getDayOfWeek().getValue()<=5).toList();
    }
}
