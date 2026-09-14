package com.company.orchestrator.solver;
import static com.company.orchestrator.solver.PlanningData.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
class ResourceSolverTest {
    private final CandidateService matcher=new CandidateService();
    private final LocalDate monday=LocalDate.of(2026,10,5);
    private Work work(long id,int hours) { return new Work(id,"Task "+id,monday,monday,hours,3,List.of(new Need(1,3,10000,"REQUIRED"))); }
    private Input input(List<Work> tasks,List<Window> windows,List<Booking> bookings) { return new Input(1,tasks,List.of(new Person(1,"Expert",40,10000,"ACTIVE",Map.of(1L,4))),windows,bookings,"test"); }
    @Test void requiresMinimumSkill() {
        var t=work(1,8);
        var i=new Input(1,List.of(t),List.of(new Person(1,"Junior",40,10000,"ACTIVE",Map.of(1L,2))),List.of(),List.of(),"test");
        assertThat(matcher.candidates(i,t)).isEmpty();
    }
    @Test void excludesLeaveAndExistingBookings() {
        var t=work(1,8);
        assertThat(matcher.candidates(input(List.of(t),List.of(new Window(1,monday,monday,10000,"LEAVE")),List.of()),t)).isEmpty();
        assertThat(matcher.candidates(input(List.of(t),List.of(),List.of(new Booking(1,monday,monday,1000))),t)).isEmpty();
    }
    @Test void capacityRespectsWorkingWeekAndWeeklyHours() {
        var t=new Work(1,"Week",monday,monday.plusDays(6),20,3,List.of());
        assertThat(matcher.candidates(input(List.of(t),List.of(),List.of()),t).getFirst().allocation()).isEqualTo(5000);
        var i=new Input(1,List.of(t),List.of(new Person(1,"Part time",20,10000,"ACTIVE",Map.of())),List.of(),List.of(),"test");
        assertThat(matcher.candidates(i,t).getFirst().allocation()).isEqualTo(10000);
    }
    @Test void timefoldLeavesGapInsteadOfOverbooking() {
        var i=input(List.of(work(1,8),work(2,8)),List.of(),List.of());
        var solution=solve(i);
        assertThat(solution.getScore().isFeasible()).isTrue();
        assertThat(solution.getAssignments().stream().filter(a -> a.getCandidate()!=null)).hasSize(1);
    }
    @Test void twoHalfTimeTasksCanShareEmployee() {
        var solution=solve(input(List.of(work(1,4),work(2,4)),List.of(),List.of()));
        assertThat(solution.getScore().isFeasible()).isTrue();
        assertThat(solution.getAssignments()).allMatch(a -> a.getCandidate()!=null);
    }
    @Test void emptyCandidateRangeProducesGap() {
        var solution=solve(input(List.of(work(1,9)),List.of(),List.of()));
        assertThat(solution.getScore().isFeasible()).isTrue();
        assertThat(solution.getAssignments().getFirst().getCandidate()).isNull();
    }
    private ResourceSolution solve(Input i) {
        var config=new SolverConfig().withSolutionClass(ResourceSolution.class).withEntityClasses(ResourceAssignment.class).withConstraintProviderClass(ResourceConstraints.class).withTerminationSpentLimit(Duration.ofMillis(150));
        return SolverFactory.<ResourceSolution>create(config).buildSolver().solve(new ResourceSolution(i.tasks().stream().map(t -> new ResourceAssignment(t,matcher.candidates(i,t))).toList()));
    }
}
