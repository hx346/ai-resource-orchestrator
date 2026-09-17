package com.company.orchestrator.solver;
import static com.company.orchestrator.solver.PlanningData.*;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.company.orchestrator.common.exception.*;
@Repository @RequiredArgsConstructor
public class PlanningRepository {
    private final JdbcTemplate db;
    public Input load(long projectId) { return load(projectId,null); }

    /**
     * excludeBookingsOfProject 非空时，快照剔除该项目的生效占用——重规划是替换而非叠加。
     * When excludeBookingsOfProject is set, the snapshot drops that project's
     * own active bookings: a replan replaces the assignment set instead of stacking on it.
     */
    public Input load(long projectId,Long excludeBookingsOfProject) {
        var projects=db.queryForList("select * from project where id=?",projectId);
        if(projects.isEmpty()) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND,projectId);
        var project=projects.getFirst();
        if(!Set.of("PLANNING","IN_PROGRESS").contains(project.get("status"))) bad("项目状态不允许编排");
        LocalDate start=date(project.get("start_date")), end=date(project.get("end_date"));
        if(start==null || end==null || end.isBefore(start) || start.plusYears(2).isBefore(end)) bad("项目须设置有效起止日期，跨度不超过两年");
        var needs=new HashMap<Long,List<Need>>();
        db.query("select r.* from task_skill_requirement r join task t on t.id=r.task_id where t.project_id=? order by r.id",rs -> {
            needs.computeIfAbsent(rs.getLong("task_id"),k -> new ArrayList<>()).add(new Need(rs.getLong("skill_id"),rs.getInt("min_level"),rs.getBigDecimal("weight").movePointRight(4).intValue(),rs.getString("requirement_type")));
        },projectId);
        List<Work> tasks=db.query("select * from task t where project_id=? and status not in ('CANCELLED','DONE') and not exists (select 1 from task c where c.parent_id=t.id) order by id",(rs,n) ->
            new Work(rs.getLong("id"),rs.getString("name"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getInt("estimated_hours"),rs.getInt("priority"),needs.getOrDefault(rs.getLong("id"),List.of())),projectId);
        if(tasks.isEmpty() || tasks.size()>200) bad("请准备 1–200 个可执行叶子任务");
        for(var t:tasks) if(t.start()==null || t.end()==null || t.start().isBefore(start) || t.end().isAfter(end) || t.end().isBefore(t.start()) || t.hours()<=0 || days(t.start(),t.end()).isEmpty()) bad("任务「"+t.name()+"」须有项目周期内的日期和正工时（周一至周五）");
        var deps=db.queryForList("select d.dependency_type,p.start_date ps,p.end_date pe,s.start_date ss,s.end_date se from task_dependency d join task p on p.id=d.predecessor_task_id join task s on s.id=d.successor_task_id where s.project_id=?",projectId);
        for(var d:deps) {
            LocalDate ps=date(d.get("ps")),pe=date(d.get("pe")),ss=date(d.get("ss")),se=date(d.get("se"));
            if(ps==null||pe==null||ss==null||se==null) bad("依赖任务日期不完整");
            boolean valid=switch(d.get("dependency_type").toString()) { case "FS" -> ss.isAfter(pe); case "SS" -> !ss.isBefore(ps); case "FF" -> !se.isBefore(pe); case "SF" -> !se.isBefore(ps); default -> false; };
            if(!valid) bad("任务日期不满足依赖关系，请调整后求解");
        }
        var skills=new HashMap<Long,Map<Long,Integer>>();
        db.query("select es.* from employee_skill es join skill s on s.id=es.skill_id where s.status='ACTIVE' order by es.id",rs -> { skills.computeIfAbsent(rs.getLong("employee_id"),k -> new HashMap<>()).put(rs.getLong("skill_id"),rs.getInt("level")); });
        var people=db.query("select * from employee order by id",(rs,n) -> new Person(rs.getLong("id"),rs.getString("name"),rs.getInt("weekly_hours"),rs.getBigDecimal("default_capacity").movePointRight(2).intValue(),rs.getString("status"),skills.getOrDefault(rs.getLong("id"),Map.of())));
        var windows=db.query("select * from employee_availability order by id",(rs,n) -> new Window(rs.getLong("employee_id"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getBigDecimal("capacity").movePointRight(2).intValue(),rs.getString("type")));
        Object[] bookingArgs=excludeBookingsOfProject==null?new Object[0]:new Object[]{excludeBookingsOfProject};
        var bookings=db.query("select * from resource_allocation where status in ('PLANNED','CONFIRMED')"+(excludeBookingsOfProject==null?"":" and project_id<>?")+" order by id",(rs,n) -> new Booking(rs.getLong("employee_id"),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getBigDecimal("allocation").movePointRight(2).intValue()),bookingArgs);
        return new Input(projectId,tasks,people,windows,bookings,hash(projectId));
    }
    /**
     * 乐观新鲜度哈希：项目域表（project/task/dependency/requirement）只取本项目行，
     * 组织域表（employee/employee_skill/skill/employee_availability/resource_allocation）保持全量——
     * 人员与跨项目占用影响任意项目的候选和容量。他项目建任务不再误伤本项目草稿。
     * Optimistic-freshness hash: project tables scope to this project, org tables stay
     * whole (they feed candidacy and cross-project capacity for every project), so
     * task creation elsewhere no longer invalidates this project's drafts.
     */
    public String hash(Long projectId) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            String filter=projectId==null?"":"where t.id="+projectId;
            for(Object[] table:new Object[][]{
                    {"project",filter},
                    {"task",projectId==null?"":"where t.project_id="+projectId},
                    {"task_dependency",projectId==null?"":"where t.successor_task_id in (select id from task where project_id="+projectId+")"},
                    {"task_skill_requirement",projectId==null?"":"where t.task_id in (select id from task where project_id="+projectId+")"},
                    {"employee",""},{"employee_skill",""},{"skill",""},{"employee_availability",""},{"resource_allocation",""}})
                for(String row:db.queryForList("select row_to_json(t)::text from "+table[0]+" t "+table[1]+" order by id",String.class)) digest.update((table[0]+row+"\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public void lockForConfirmation() {
        db.execute("lock table project,task,task_dependency,task_skill_requirement,employee,employee_skill,skill,employee_availability,resource_allocation,resource_plan,resource_plan_item in share row exclusive mode");
    }
    private static LocalDate date(Object value) { return value==null?null:LocalDate.parse(value.toString()); }
    public static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST,message); }
}
