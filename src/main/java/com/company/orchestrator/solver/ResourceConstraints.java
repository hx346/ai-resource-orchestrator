package com.company.orchestrator.solver;
import java.time.LocalDate;
import java.util.*;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
public class ResourceConstraints implements ConstraintProvider {
    @Override public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[]{
            f.forEach(ResourceAssignment.class)
                .groupBy(a -> a.getCandidate().employeeId(),ConstraintCollectors.toList())
                .penalize(HardSoftScore.ONE_HARD,(id,items) -> excess(items)).asConstraint("Daily capacity"),
            f.forEachIncludingUnassigned(ResourceAssignment.class).filter(a -> a.getCandidate()==null)
                .penalize(HardSoftScore.ONE_SOFT,a -> 10000*(6-a.getTask().priority())).asConstraint("Unassigned work"),
            f.forEach(ResourceAssignment.class).reward(HardSoftScore.ONE_SOFT,a -> a.getCandidate().score()).asConstraint("Skill match")
        };
    }
    public static int excess(List<ResourceAssignment> items) {
        Map<LocalDate,Integer> usage=new HashMap<>(), limits=new HashMap<>();
        for(var a:items) for(var day:a.getCandidate().remaining().keySet()) {
            usage.merge(day,a.getCandidate().allocation(),Integer::sum);
            limits.merge(day,a.getCandidate().remaining().get(day),Math::min);
        }
        return usage.entrySet().stream().mapToInt(e -> Math.max(0,e.getValue()-limits.get(e.getKey()))).sum();
    }
}
