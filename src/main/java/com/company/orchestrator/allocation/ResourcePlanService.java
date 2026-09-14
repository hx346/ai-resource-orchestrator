package com.company.orchestrator.allocation;
import static com.company.orchestrator.solver.PlanningData.*;
import static com.company.orchestrator.solver.PlanningRepository.bad;
import java.util.*;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.orchestrator.solver.*;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.company.orchestrator.common.exception.*;

@Service @RequiredArgsConstructor
public class ResourcePlanService {
    private final PlanningRepository repository;
    private final CandidateService candidates;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Map<String,Object>> candidates(long projectId) {
        var input=repository.load(projectId);
        return input.tasks().stream().map(t -> Map.<String,Object>of("taskId",t.id(),"taskName",t.name(),"candidates",candidates.candidates(input,t))).toList();
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.REPEATABLE_READ)
    public long solve(long projectId,String strategy,String username) {
        if(!Weights.STRATEGIES.contains(strategy)) bad("不支持的策略："+strategy+"，可选 "+Weights.STRATEGIES.stream().sorted().toList());
        var weights=Weights.of(strategy);
        // Serialize version allocation per project; confirmation will revalidate the input snapshot.
        db.queryForList("select id from project where id=? for update",projectId);
        if(db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('CONFIRMED','PLANNED')",Long.class,projectId)>0) bad("项目已有生效分配，请先撤销该方案");
        var input=repository.load(projectId);
        var initial=new ResourceSolution(input.tasks().stream().map(t -> new ResourceAssignment(t,candidates.candidates(input,t),weights)).toList());
        var config=new SolverConfig().withSolutionClass(ResourceSolution.class).withEntityClasses(ResourceAssignment.class)
            .withConstraintProviderClass(ResourceConstraints.class).withTerminationSpentLimit(Duration.ofSeconds(2));
        long started=System.nanoTime();
        ResourceSolution solution=SolverFactory.<ResourceSolution>create(config).buildSolver().solve(initial);
        if(!solution.getScore().isFeasible()) bad("未找到满足硬约束的方案");
        int version=db.queryForObject("select coalesce(max(version),0)+1 from resource_plan where project_id=?",Integer.class,projectId);
        long id=db.queryForObject("insert into resource_plan(project_id,version,strategy,score_text,input_hash,solver_duration,created_by) values (?,?,?,?,?,?,(select id from sys_user where username=?)) returning id",Long.class,
            projectId,version,strategy,solution.getScore().toString(),input.hash(),(System.nanoTime()-started)/1_000_000,username);
        saveItems(id,projectId,solution.getAssignments(),input);
        return id;
    }
    public Map<String,Object> get(long id) {
        var rows=db.queryForList("select * from resource_plan where id=?",id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST,"方案不存在");
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("items",db.queryForList("select i.*,e.name employee_name,t.name task_name from resource_plan_item i join employee e on e.id=i.employee_id join task t on t.id=i.task_id where plan_id=? order by i.id",id));
        try { var gaps=json.readTree(result.get("gaps").toString()); result.put("gaps",gaps); result.put("gapSummary",GapAnalysis.summarize(gaps)); } catch(Exception ex) { throw new IllegalStateException(ex); }
        return result;
    }
    public List<Map<String,Object>> list(long projectId) { return db.queryForList("select id,version,strategy,status,score_text,created_at from resource_plan where project_id=? order by version desc",projectId); }
    /** Side-by-side comparison of two plans of the same project: per-task assignment plus score/gap summary. / 同项目两方案并排对比。 */
    @SuppressWarnings("unchecked")
    public Map<String,Object> compare(long leftId,long rightId) {
        var left=get(leftId); var right=get(rightId);
        if(!left.get("project_id").equals(right.get("project_id"))) bad("只能对比同一项目的方案");
        var rows=new LinkedHashMap<Long,Map<String,Object>>();
        for(var side:new Map<?,?>[]{left,right}) {
            boolean isLeft=side==left;
            for(var item:(List<Map<String,Object>>)side.get("items")) {
                long taskId=((Number)item.get("task_id")).longValue();
                rows.computeIfAbsent(taskId,k -> new LinkedHashMap<>(Map.of("taskId",k,"taskName",item.get("task_name"),"left",Map.of(),"right",Map.of())))
                    .put(isLeft?"left":"right",Map.of("employeeName",item.get("employee_name"),"allocation",item.get("allocation")));
            }
            for(var gap:(com.fasterxml.jackson.databind.JsonNode)side.get("gaps")) {
                long taskId=gap.get("taskId").asLong();
                rows.computeIfAbsent(taskId,k -> new LinkedHashMap<>(Map.of("taskId",k,"taskName",gap.get("taskName").asText(),"left",Map.of(),"right",Map.of())))
                    .put(isLeft?"left":"right",Map.of("gap",true));
            }
        }
        java.util.function.Function<Map<String,Object>,Map<String,Object>> meta=p -> Map.of("id",p.get("id"),"version",p.get("version"),"strategy",p.get("strategy"),"score",p.get("score_text"),"status",p.get("status"),"gaps",((com.fasterxml.jackson.databind.JsonNode)p.get("gaps")).size());
        return Map.of("left",meta.apply(left),"right",meta.apply(right),"rows",new ArrayList<>(rows.values()));
    }
    public record Selection(long taskId,Long employeeId) {}
    @Transactional(rollbackFor=Exception.class)
    public void edit(long id,List<Selection> selections) {
        repository.lockForConfirmation();
        var plan=get(id); requireDraft(plan);
        long projectId=((Number)plan.get("project_id")).longValue();
        var input=repository.load(projectId); requireFresh(plan,input.hash());
        if(selections.size()!=input.tasks().size() || selections.stream().map(Selection::taskId).distinct().count()!=selections.size()) bad("须提交全部任务，每个任务仅一次");
        var assignments=new ArrayList<ResourceAssignment>();
        for(var t:input.tasks()) {
            var s=selections.stream().filter(v -> v.taskId()==t.id()).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,"任务不匹配"));
            var a=new ResourceAssignment(t,candidates.candidates(input,t));
            if(s.employeeId()!=null) a.setCandidate(a.getCandidates().stream().filter(c -> c.employeeId()==s.employeeId()).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,"员工不满足技能或可用容量要求")));
            assignments.add(a);
        }
        var grouped=new HashMap<Long,List<ResourceAssignment>>();
        assignments.stream().filter(a -> a.getCandidate()!=null).forEach(a -> grouped.computeIfAbsent(a.getCandidate().employeeId(),k -> new ArrayList<>()).add(a));
        if(grouped.values().stream().anyMatch(a -> ResourceConstraints.excess(a)>0)) bad("人工调整导致人员超配");
        db.update("delete from resource_plan_item where plan_id=?",id);
        saveItems(id,projectId,assignments,input);
        db.update("update resource_plan set score_text='MANUAL / feasible',updated_at=now() where id=?",id);
    }
    @Transactional(rollbackFor=Exception.class)
    public void confirm(long id) {
        repository.lockForConfirmation();
        var plan=get(id);
        if("CONFIRMED".equals(plan.get("status"))) return;
        requireDraft(plan); requireFresh(plan,repository.hash());
        if(!"[]".equals(plan.get("gaps").toString())) bad("方案仍有未分配任务，不能确认");
        long projectId=((Number)plan.get("project_id")).longValue();
        if(db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('CONFIRMED','PLANNED')",Long.class,projectId)>0) bad("项目已有生效分配");
        db.update("insert into resource_allocation(project_id,task_id,employee_id,start_date,end_date,allocation,status,plan_id) select project_id,task_id,employee_id,start_date,end_date,allocation,'CONFIRMED',plan_id from resource_plan_item where plan_id=?",id);
        db.update("update resource_plan set status='CONFIRMED',updated_at=now() where id=?",id);
    }
    @Transactional(rollbackFor=Exception.class)
    public void cancel(long id) {
        repository.lockForConfirmation();
        var plan=get(id);
        if("ARCHIVED".equals(plan.get("status"))) return;
        db.update("update resource_allocation set status='CANCELLED',updated_at=now() where plan_id=?",id);
        db.update("update resource_plan set status='ARCHIVED',updated_at=now() where id=?",id);
    }
    private void saveItems(long id,long projectId,List<ResourceAssignment> assignments,Input input) {
        var gaps=new ArrayList<Map<String,Object>>();
        // 技能名称只在存在缺口时才查询 / resolve skill names only when gaps exist
        Map<Long,String> skillNames=assignments.stream().anyMatch(a -> a.getCandidate()==null)
            ? db.query("select id,name from skill order by id",rs -> { var names=new HashMap<Long,String>(); while(rs.next()) names.put(rs.getLong("id"),rs.getString("name")); return names; }) : Map.of();
        for(var a:assignments) {
            var t=a.getTask(); var c=a.getCandidate();
            if(c==null) {
                var missing=GapAnalysis.missingSkills(input,t);
                gaps.add(Map.of("taskId",t.id(),"taskName",t.name(),"reason",a.getCandidates().isEmpty()?"无满足技能和容量的候选员工":"人员时间冲突，当前方案未分配",
                    "skillGap",!missing.isEmpty(),"missingSkills",missing.stream().map(n -> Map.of("skillId",n.skillId(),"skillName",skillNames.getOrDefault(n.skillId(),"技能#"+n.skillId()),"requiredLevel",n.minLevel())).toList(),
                    "hours",t.hours(),"start",t.start().toString(),"end",t.end().toString()));
                continue;
            }
            db.update("insert into resource_plan_item(plan_id,project_id,task_id,employee_id,start_date,end_date,allocation) values (?,?,?,?,?,?,?)",id,projectId,t.id(),c.employeeId(),t.start(),t.end(),BigDecimal.valueOf(c.allocation(),2));
        }
        try { db.update("update resource_plan set gaps=? where id=?",json.writeValueAsString(gaps),id); } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private void requireDraft(Map<String,Object> plan) { if(!"DRAFT".equals(plan.get("status"))) bad("仅草稿方案允许此操作"); }
    private void requireFresh(Map<String,Object> plan,String hash) { if(!hash.equals(plan.get("input_hash"))) throw new BusinessException(ErrorCode.DUPLICATE,"基础数据已变化，请重新求解"); }
}
