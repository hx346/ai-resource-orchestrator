package com.company.orchestrator.solver;
import java.util.*;
import lombok.*;
import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
@PlanningSolution @Getter @Setter @NoArgsConstructor
public class ResourceSolution {
    @PlanningEntityCollectionProperty private List<ResourceAssignment> assignments;
    @PlanningScore private HardSoftScore score;
    public ResourceSolution(List<ResourceAssignment> assignments) { this.assignments=assignments; }
}
