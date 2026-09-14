package com.company.orchestrator.solver;
import java.util.*;
import lombok.*;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import static com.company.orchestrator.solver.PlanningData.*;
@PlanningEntity @Getter @Setter @NoArgsConstructor
public class ResourceAssignment {
    @PlanningId private Long id;
    private Work task;
    @ValueRangeProvider(id="candidates") private List<Candidate> candidates;
    @PlanningVariable(valueRangeProviderRefs="candidates",nullable=true) private Candidate candidate;
    private Weights weights;
    public ResourceAssignment(Work task,List<Candidate> candidates) { this(task,candidates,Weights.of("BALANCED")); }
    public ResourceAssignment(Work task,List<Candidate> candidates,Weights weights) { this.id=task.id(); this.task=task; this.candidates=candidates; this.weights=weights; }
}
