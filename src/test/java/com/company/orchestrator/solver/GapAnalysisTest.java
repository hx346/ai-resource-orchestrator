package com.company.orchestrator.solver;
import static com.company.orchestrator.solver.PlanningData.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
class GapAnalysisTest {
    private final LocalDate monday=LocalDate.of(2026,10,5);
    private Input people(Person... list) { return new Input(1,List.of(),List.of(list),List.of(),List.of(),"test"); }
    @Test void flagsOnlyRequiredSkillsNobodyReaches() {
        var task=new Work(1,"Train YOLO",monday,monday,8,3,List.of(new Need(1,4,10000,"REQUIRED"),new Need(2,3,10000,"PREFERRED")));
        // 员工仅有技能1 L2（未达 L4）；技能2 缺失但为 PREFERRED，不算硬缺口 / skill 1 below level, skill 2 only PREFERRED
        assertThat(GapAnalysis.missingSkills(people(new Person(1,"Junior",40,10000,"ACTIVE",Map.of(1L,2))),task)).containsExactly(new Need(1,4,10000,"REQUIRED"));
    }
    @Test void inactiveExpertDoesNotCoverTheGap() {
        var task=new Work(1,"Deploy",monday,monday,8,3,List.of(new Need(1,5,10000,"REQUIRED")));
        assertThat(GapAnalysis.missingSkills(people(new Person(1,"Leaving",40,10000,"INACTIVE",Map.of(1L,5))),task)).hasSize(1);
    }
    @Test void aggregatesPerSkillAndSkipsCapacityConflicts() throws Exception {
        var json=new ObjectMapper().readTree("""
            [{"taskId":1,"taskName":"A","reason":"人员时间冲突，当前方案未分配","skillGap":true,"missingSkills":[{"skillId":7,"skillName":"YOLO","requiredLevel":4}],"hours":8,"start":"2026-10-05","end":"2026-10-06"},
             {"taskId":2,"taskName":"B","reason":"人员时间冲突，当前方案未分配","skillGap":true,"missingSkills":[{"skillId":7,"skillName":"YOLO","requiredLevel":4}],"hours":16,"start":"2026-10-08","end":"2026-10-09"},
             {"taskId":3,"taskName":"C","reason":"无满足技能和容量的候选员工","skillGap":false,"missingSkills":[],"hours":40,"start":"2026-10-05","end":"2026-10-09"}]""");
        var summary=GapAnalysis.summarize(json);
        assertThat(summary).hasSize(1);
        var row=summary.getFirst();
        assertThat(row.get("skillName")).isEqualTo("YOLO");
        assertThat(row.get("taskCount")).isEqualTo(2);
        assertThat(row.get("totalHours")).isEqualTo(24);
        assertThat(row.get("start")).isEqualTo("2026-10-05");
        assertThat(row.get("end")).isEqualTo("2026-10-09");
        assertThat(row.get("workdays")).isEqualTo(5); // 2026-10-05 周一 至 2026-10-09 周五 / Mon–Fri
    }
}
