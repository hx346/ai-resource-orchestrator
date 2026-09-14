package com.company.orchestrator.solver;
import java.time.LocalDate;
import java.util.*;
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
