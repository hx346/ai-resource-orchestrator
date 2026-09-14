package com.company.orchestrator.solver;
import static com.company.orchestrator.solver.PlanningData.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
/** Classifies plan gaps: a true skill gap (no active employee reaches the required level) versus a capacity/time conflict. / 区分缺口类型：技能缺口（全公司无人达标）与时间容量冲突。 */
public final class GapAnalysis {
    private GapAnalysis() {}
    /** REQUIRED needs that no ACTIVE employee satisfies at the required level. / 无任何在职员工达到等级的必备技能。 */
    public static List<Need> missingSkills(Input input,Work task) {
        return task.needs().stream().filter(n -> "REQUIRED".equals(n.type()) && input.people().stream()
            .filter(p -> "ACTIVE".equals(p.status())).noneMatch(p -> p.skills().getOrDefault(n.skillId(),0)>=n.minLevel())).toList();
    }
    /** Aggregates persisted gap entries into one row per missing skill (tasks, hours, window, workdays). / 按缺失技能聚合缺口条目。 */
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> summarize(JsonNode gaps) {
        if(gaps==null || !gaps.isArray() || gaps.isEmpty()) return List.of();
        var bySkill=new LinkedHashMap<Long,Map<String,Object>>();
        for(var gap:gaps) {
            var missing=gap.get("missingSkills"); if(missing==null || !missing.isArray() || missing.isEmpty()) continue;
            var start=LocalDate.parse(gap.get("start").asText()); var end=LocalDate.parse(gap.get("end").asText());
            for(var skill:missing) {
                long id=skill.get("skillId").asLong();
                var row=(Map<String,Object>)bySkill.get(id);
                if(row==null) { row=new LinkedHashMap<>(); row.put("skillId",id); row.put("skillName",skill.get("skillName").asText()); row.put("requiredLevel",skill.get("requiredLevel").asInt()); row.put("taskCount",0); row.put("totalHours",0); row.put("start",start); row.put("end",end); bySkill.put(id,row); }
                row.put("taskCount",(int)row.get("taskCount")+1); row.put("totalHours",(int)row.get("totalHours")+gap.get("hours").asInt());
                if(start.isBefore((LocalDate)row.get("start"))) row.put("start",start);
                if(end.isAfter((LocalDate)row.get("end"))) row.put("end",end);
            }
        }
        var result=new ArrayList<>(bySkill.values());
        for(var row:result) { row.put("workdays",days((LocalDate)row.get("start"),(LocalDate)row.get("end")).size()); row.put("start",row.get("start").toString()); row.put("end",row.get("end").toString()); }
        result.sort(Comparator.comparingInt((Map<String,Object> r) -> (int)r.get("taskCount")).reversed());
        return result;
    }
}
