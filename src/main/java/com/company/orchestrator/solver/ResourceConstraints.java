package com.company.orchestrator.solver;
import java.time.LocalDate;
import java.util.*;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
public class ResourceConstraints implements ConstraintProvider {
    /** LOW_RISK 把单人任务占用合计压在该阈值以下（bps，60%），鼓励分散到更多人 / soft ceiling for per-person total allocation. */
    private static final int CONCENTRATION_LIMIT=6000;
    @Override public Constraint[] defineConstraints(ConstraintFactory f) {
        return new Constraint[]{
            f.forEach(ResourceAssignment.class)
                .groupBy(a -> a.getCandidate().employeeId(),ConstraintCollectors.toList())
                .penalize(HardSoftScore.ONE_HARD,(id,items) -> excess(items)).asConstraint("Daily capacity"),
            f.forEach(ResourceAssignment.class)
                .groupBy(a -> a.getCandidate().employeeId(),ConstraintCollectors.toList())
                .penalize(HardSoftScore.ONE_SOFT,(id,items) -> items.getFirst().getWeights().balance()*concentration(items)).asConstraint("Load concentration"),
            f.forEachIncludingUnassigned(ResourceAssignment.class).filter(a -> a.getCandidate()==null)
                .penalize(HardSoftScore.ONE_SOFT,a -> a.getWeights().unassigned()*(6-a.getTask().priority())).asConstraint("Unassigned work"),
            f.forEach(ResourceAssignment.class).reward(HardSoftScore.ONE_SOFT,a -> a.getWeights().skill()*a.getCandidate().score()).asConstraint("Skill match")
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
    public static int concentration(List<ResourceAssignment> items) { return Math.max(0,items.stream().mapToInt(a -> a.getCandidate().allocation()).sum()-CONCENTRATION_LIMIT); }
}
