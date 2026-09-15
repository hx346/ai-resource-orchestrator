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
    /** Weekly cross-project load (percent) at which a plan carries a warning / 触发跨项目预警的周负载阈值。 */
    private static final int WARN_LOAD=80;
    private final PlanningRepository repository;
    private final CandidateService candidates;
    private final com.company.orchestrator.system.NotifyService notify;
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
        // Serialize version allocation per project; confirmation will revalidate the input snapshot.
        db.queryForList("select id from project where id=? for update",projectId);
        if(db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('CONFIRMED','PLANNED')",Long.class,projectId)>0) bad("项目已有生效分配，请使用重新求解（重规划）生成替代方案，或先撤销现有方案");
        return solveAndPersist(projectId,strategy,repository.load(projectId),username);
    }

    /**
     * 动态重规划：保留旧方案生效的同时按当前基础数据重新求解（快照剔除本项目自身占用），
     * 确认新方案时才原子换班。对应链路 Event → Impact → Solver → 新方案 → 人工确认。
     * Dynamic replanning: re-solve against current data while the old set
     * stays active (own bookings excluded); confirming the new plan swaps atomically.
     */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.REPEATABLE_READ)
    public long replan(long projectId,String strategy,String username) {
        if(!Weights.STRATEGIES.contains(strategy)) bad("不支持的策略："+strategy+"，可选 "+Weights.STRATEGIES.stream().sorted().toList());
        db.queryForList("select id from project where id=? for update",projectId);
        if(db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('CONFIRMED','PLANNED')",Long.class,projectId)==0) bad("项目没有生效分配，请直接生成资源方案");
        return solveAndPersist(projectId,strategy,repository.load(projectId,projectId),username);
    }

    private long solveAndPersist(long projectId,String strategy,Input input,String username) {
        var weights=Weights.of(strategy);
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

    /** 变更影响分析：生效分配 × 当前基础数据的具体冲突（休假/停用/任务漂移/技能要求/项目周期），不依赖全局哈希 / concrete conflicts of the active allocation set. */
    @Transactional(readOnly=true)
    public Map<String,Object> impact(long projectId) {
        var active=db.queryForList("select id,version from resource_plan where project_id=? and status='CONFIRMED' order by version desc limit 1",projectId);
        if(active.isEmpty()) bad("项目没有已确认生效的方案，无需影响分析");
        long planId=((Number)active.getFirst().get("id")).longValue();
        var conflicts=conflictsOf(projectId,planId);
        return Map.of("planId",planId,"version",((Number)active.getFirst().get("version")).intValue(),"conflicts",conflicts,"conflictCount",conflicts.size());
    }

    /** 全局重规划巡检：事件不需要主动上报，任一生效方案与最新数据的冲突都可被检出（Phase 4 事件自动触发的巡检形态）/ scan every active plan for replan-worthy conflicts. */
    @Transactional(readOnly=true)
    public List<Map<String,Object>> alerts() {
        var actives=db.queryForList("select rp.id plan_id, rp.version, p.id project_id, p.name project_name from resource_plan rp join project p on p.id=rp.project_id where rp.status='CONFIRMED' order by p.id");
        var result=new ArrayList<Map<String,Object>>();
        for(var a:actives) {
            var conflicts=conflictsOf(((Number)a.get("project_id")).longValue(),((Number)a.get("plan_id")).longValue());
            if(!conflicts.isEmpty()) result.add(Map.of("projectId",((Number)a.get("project_id")).longValue(),"projectName",a.get("project_name"),
                "planId",((Number)a.get("plan_id")).longValue(),"version",((Number)a.get("version")).intValue(),
                "conflictCount",conflicts.size(),"conflicts",conflicts.stream().limit(3).toList()));
        }
        return result;
    }

    /** 影响分析与巡检共用的五类冲突查询 / the five conflict queries shared by impact() and alerts(). */
    private List<Map<String,Object>> conflictsOf(long projectId,long planId) {
        var conflicts=new ArrayList<Map<String,Object>>();
        // 与求解器同规则：非 AVAILABLE 窗口即容量清零 / same rule as the solver: non-AVAILABLE windows zero out capacity
        db.queryForList("""
            select a.employee_id,e.name employee_name,a.task_id,t.name task_name,a.start_date a_start,a.end_date a_end,w.type w_type,w.start_date w_start,w.end_date w_end
            from resource_allocation a join employee e on e.id=a.employee_id join task t on t.id=a.task_id
            join employee_availability w on w.employee_id=a.employee_id and w.type<>'AVAILABLE' and w.start_date<=a.end_date and w.end_date>=a.start_date
            where a.plan_id=? and a.status='CONFIRMED' order by a.id""",planId)
            .forEach(r -> conflicts.add(conflict("UNAVAILABLE",r,"分配 %s→%s 与 %s 窗口 %s→%s 重叠，期间容量清零".formatted(r.get("a_start"),r.get("a_end"),r.get("w_type"),r.get("w_start"),r.get("w_end")))));
        db.queryForList("""
            select a.employee_id,e.name employee_name,a.task_id,t.name task_name
            from resource_allocation a join employee e on e.id=a.employee_id join task t on t.id=a.task_id
            where a.plan_id=? and a.status='CONFIRMED' and e.status<>'ACTIVE'""",planId)
            .forEach(r -> conflicts.add(conflict("EMPLOYEE_INACTIVE",r,"成员当前状态非 ACTIVE，无法继续承担分配")));
        db.queryForList("""
            select a.employee_id,e.name employee_name,a.task_id,t.name task_name,a.start_date a_start,a.end_date a_end,t.start_date t_start,t.end_date t_end,t.status t_status
            from resource_allocation a join task t on t.id=a.task_id join employee e on e.id=a.employee_id
            where a.plan_id=? and a.status='CONFIRMED' and (a.start_date<>t.start_date or a.end_date<>t.end_date or t.status in ('CANCELLED','DONE'))""",planId)
            .forEach(r -> conflicts.add(conflict("TASK_DRIFT",r,"任务日期/状态已变化：分配 %s→%s，任务现为 %s→%s（%s）".formatted(r.get("a_start"),r.get("a_end"),r.get("t_start"),r.get("t_end"),r.get("t_status")))));
        db.queryForList("""
            select a.employee_id,e.name employee_name,r.task_id,t.name task_name,s.name skill_name,r.min_level,coalesce(es.level,0) current_level
            from resource_allocation a join task_skill_requirement r on r.task_id=a.task_id and r.requirement_type='REQUIRED'
            join skill s on s.id=r.skill_id join employee e on e.id=a.employee_id join task t on t.id=a.task_id
            left join employee_skill es on es.employee_id=a.employee_id and es.skill_id=r.skill_id
            where a.plan_id=? and a.status='CONFIRMED' and (s.status<>'ACTIVE' or coalesce(es.level,0)<r.min_level)""",planId)
            .forEach(r -> conflicts.add(conflict("SKILL_DRIFT",r,"%s 要求 L%s，成员当前 L%s（技能或画像已变化）".formatted(r.get("skill_name"),r.get("min_level"),r.get("current_level")))));
        db.queryForList("""
            select a.employee_id,e.name employee_name,a.task_id,t.name task_name,a.start_date a_start,a.end_date a_end
            from resource_allocation a join task t on t.id=a.task_id join employee e on e.id=a.employee_id, project p
            where p.id=? and a.plan_id=? and a.status='CONFIRMED' and (a.start_date<p.start_date or a.end_date>p.end_date)""",projectId,planId)
            .forEach(r -> conflicts.add(conflict("PROJECT_WINDOW",r,"项目周期已变化，分配 %s→%s 落在窗口之外".formatted(r.get("a_start"),r.get("a_end")))));
        return conflicts;
    }

    private Map<String,Object> conflict(String type,Map<String,Object> row,String detail) {
        var result=new LinkedHashMap<String,Object>();
        result.put("type",type);
        result.put("employeeId",((Number)row.get("employee_id")).longValue());
        result.put("employeeName",row.get("employee_name"));
        if(row.get("task_id")!=null) { result.put("taskId",((Number)row.get("task_id")).longValue()); result.put("taskName",row.get("task_name")); }
        result.put("detail",detail);
        return result;
    }
    public Map<String,Object> get(long id) {
        var rows=db.queryForList("select * from resource_plan where id=?",id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST,"方案不存在");
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("items",db.queryForList("select i.*,e.name employee_name,t.name task_name from resource_plan_item i join employee e on e.id=i.employee_id join task t on t.id=i.task_id where plan_id=? order by i.id",id));
        try { var gaps=json.readTree(result.get("gaps").toString()); result.put("gaps",gaps); result.put("gapSummary",GapAnalysis.summarize(gaps)); } catch(Exception ex) { throw new IllegalStateException(ex); }
        result.put("warnings",warnings(id));
        return result;
    }
    /** Cross-project weekly load warnings: this plan's items plus other projects' active allocations; any week >= 80% (bps) is flagged. / 跨项目周负载预警：本方案条目叠加其他项目生效分配，任一周 ≥80% 提示。 */
    private List<Map<String,Object>> warnings(long planId) {
        var own=db.queryForList("select i.employee_id,e.name employee_name,i.start_date,i.end_date,i.allocation from resource_plan_item i join employee e on e.id=i.employee_id where i.plan_id=?",planId);
        if(own.isEmpty()) return List.of();
        var others=db.queryForList("select a.employee_id,e.name employee_name,a.start_date,a.end_date,a.allocation from resource_allocation a join employee e on e.id=a.employee_id where a.status in ('PLANNED','CONFIRMED') and a.plan_id<>? and a.end_date>=current_date",planId);
        var names=new LinkedHashMap<Long,String>(); var load=new HashMap<Long,TreeMap<String,Integer>>();
        var today=java.time.LocalDate.now(); var first=today.minusDays(today.getDayOfWeek().getValue()-1);
        for(var r:java.util.stream.Stream.concat(own.stream(),others.stream()).toList()) {
            long employee=((Number)r.get("employee_id")).longValue();
            names.putIfAbsent(employee,(String)r.get("employee_name"));
            var start=java.time.LocalDate.parse(r.get("start_date").toString()); var end=java.time.LocalDate.parse(r.get("end_date").toString());
            int allocation=((BigDecimal)r.get("allocation")).intValue();
            for(var monday=first;!monday.isAfter(end);monday=monday.plusWeeks(1))
                if(!end.isBefore(monday) && !start.isAfter(monday.plusDays(6))) load.computeIfAbsent(employee,k -> new TreeMap<>()).merge(monday.toString(),allocation,Integer::sum);
        }
        var result=new ArrayList<Map<String,Object>>();
        load.forEach((employee,weeks) -> weeks.forEach((week,total) -> { if(total>=WARN_LOAD) result.add(Map.of("employeeId",employee,"employeeName",names.get(employee),"week",week,"load",total)); }));
        result.sort((a,b) -> { int c=a.get("week").toString().compareTo(b.get("week").toString()); return c!=0?c:a.get("employeeName").toString().compareTo(b.get("employeeName").toString()); });
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
        // 重规划换班：确认新方案时原子归档本项目旧生效分配与方案 / atomic swap: archive the previous active set while confirming a replacement
        db.update("update resource_allocation set status='CANCELLED',updated_at=now() where project_id=? and status in ('CONFIRMED','PLANNED') and plan_id<>?",projectId,id);
        db.update("update resource_plan set status='ARCHIVED',updated_at=now() where project_id=? and status='CONFIRMED' and id<>?",projectId,id);
        db.update("insert into resource_allocation(project_id,task_id,employee_id,start_date,end_date,allocation,status,plan_id) select project_id,task_id,employee_id,start_date,end_date,allocation,'CONFIRMED',plan_id from resource_plan_item where plan_id=?",id);
        db.update("update resource_plan set status='CONFIRMED',updated_at=now() where id=?",id);
        // 提交后推送确认通知（off 模式为空操作）/ push after commit; no-op when notify is off
        notify.planConfirmed(projectId,((Number)plan.get("version")).intValue(),((List<?>)plan.get("items")).size(),plan.get("score_text").toString());
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
