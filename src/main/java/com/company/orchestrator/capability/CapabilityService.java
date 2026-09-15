package com.company.orchestrator.capability;

import static com.company.orchestrator.solver.PlanningData.days;
import static com.company.orchestrator.solver.PlanningRepository.bad;

import java.time.LocalDate;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 组织能力决策（Phase 6 第一片）：技能供需 Gap 预测、关键能力节点、核心人员离开影响。
 * 全部基于内部数据（任务需求 × 员工画像 × 生效分配），只读、不改变任何决策。
 * Organizational capability decisions: skill supply/demand gaps, key
 * capability nodes, and leave impact — read-only analytics over internal data.
 *
 * 口径（保守估计）/ method notes:
 * - 需求：预测窗口内未完成任务的技能需求工时（含已分配——供给同步扣减占用）；
 *   达标线取该技能在任务需求中的最高 min_level。
 * - 供给：达到线的 ACTIVE 员工的窗口可用工时 = 工作日×8×默认容量% − 生效分配占用 − 不可用窗口。
 * - 缺口人数按 0.8 有效投入系数折算 / gap people assume an 0.8 effective-utilization factor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityService {

    /** 有效投入系数（培训/会议/切换损耗）/ effective utilization factor. */
    private static final double EFFECTIVE = 0.8;
    /** 达标人数 ≤ 该值视为瓶颈技能 / skills with at most this many qualified people are bottlenecks. */
    private static final int BOTTLENECK_QUALIFIED = 2;

    private final JdbcTemplate db;

    /** 技能供需 Gap 预测：支撑招聘 / 培训 / 外包 / 调配决策 / skill supply-demand gap forecast. */
    @Transactional(readOnly = true)
    public Map<String, Object> supplyDemand(int weeks) {
        weeks = Math.max(4, Math.min(52, weeks));
        LocalDate start = LocalDate.now(), end = start.plusWeeks(weeks);
        int workdays = days(start, end).size();
        var loads = loads(start, end);
        var rows = new ArrayList<Map<String, Object>>();
        var demand = db.queryForList("""
                select r.skill_id, s.name skill_name, max(r.min_level) required_level,
                       sum(u.hours) demand_hours, count(distinct u.task_id) task_count
                from (
                    select distinct t.id task_id, t.estimated_hours hours
                    from task t
                    where t.status not in ('CANCELLED','DONE') and t.start_date <= ? and t.end_date >= ?
                ) u
                join task_skill_requirement r on r.task_id = u.task_id
                join skill s on s.id = r.skill_id and s.status = 'ACTIVE'
                group by r.skill_id, s.name
                order by demand_hours desc""", end, start);
        for (var d : demand) {
            long skillId = ((Number) d.get("skill_id")).longValue();
            int requiredLevel = ((Number) d.get("required_level")).intValue();
            long demandHours = ((Number) d.get("demand_hours")).longValue();
            var qualified = db.queryForList("""
                    select e.id, e.default_capacity from employee_skill es
                    join employee e on e.id = es.employee_id
                    where es.skill_id = ? and es.level >= ? and e.status = 'ACTIVE'""", skillId, requiredLevel);
            long availableHours = 0;
            for (var person : qualified) {
                int[] load = loads.getOrDefault(((Number) person.get("id")).longValue(), new int[2]);
                long capacityHours = (long) (workdays * 8 * ((Number) person.get("default_capacity")).doubleValue() / 100.0);
                availableHours += Math.max(0, capacityHours - load[0] - load[1]);
            }
            long gapHours = Math.max(0, demandHours - availableHours);
            int gapPeople = gapHours > 0 ? (int) Math.ceil(gapHours / (workdays * 8 * EFFECTIVE)) : 0;
            String advice = gapPeople == 0 ? "供给充足"
                    : qualified.isEmpty() ? "全公司无人达标：优先招聘或外包"
                    : "有人但容量不足：内部调配、培训或招聘";
            rows.add(new LinkedHashMap<>(Map.of("skillId", skillId, "skillName", d.get("skill_name"), "requiredLevel", requiredLevel,
                    "demandHours", demandHours, "taskCount", ((Number) d.get("task_count")).intValue(),
                    "qualifiedCount", qualified.size(), "availableHours", availableHours,
                    "gapHours", gapHours, "gapPeople", gapPeople, "advice", advice)));
        }
        int shortage = (int) rows.stream().filter(r -> ((Number) r.get("gapPeople")).intValue() > 0).count();
        long totalGap = rows.stream().mapToLong(r -> ((Number) r.get("gapHours")).longValue()).sum();
        return Map.of("weeks", weeks, "start", start.toString(), "end", end.toString(), "workdays", workdays,
                "rows", rows, "summary", Map.of("shortageSkills", shortage, "totalGapHours", totalGap, "skillsTracked", rows.size()));
    }

    /** 关键能力节点：掌握瓶颈技能（达标人数 ≤2）且被项目争夺的人 / people holding bottleneck skills. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> keyPeople() {
        LocalDate start = LocalDate.now(), end = start.plusWeeks(12);
        var loads = loads(start, end);
        var bottleneck = db.queryForList("""
                select d.skill_id, d.name skill_name, d.required_level,
                       (select count(*) from employee_skill es join employee e on e.id = es.employee_id
                        where es.skill_id = d.skill_id and es.level >= d.required_level and e.status = 'ACTIVE') qualified_count
                from (
                    select r.skill_id, s.name, max(r.min_level) required_level
                    from task_skill_requirement r join skill s on s.id = r.skill_id
                    join task t on t.id = r.task_id and t.status not in ('CANCELLED','DONE')
                    group by r.skill_id, s.name
                ) d""");
        var bySkill = new HashMap<Long, Map<String, Object>>();
        for (var b : bottleneck) {
            b.put("qualifiedCount", ((Number) b.remove("qualified_count")).intValue());
            b.put("skillId", ((Number) b.remove("skill_id")).longValue());
            b.put("skillName", b.remove("skill_name"));
            b.put("requiredLevel", ((Number) b.remove("required_level")).intValue());
            if (((Number) b.get("qualifiedCount")).intValue() <= BOTTLENECK_QUALIFIED) bySkill.put((Long) b.get("skillId"), b);
        }
        if (bySkill.isEmpty()) return List.of();
        var result = new ArrayList<Map<String, Object>>();
        for (var person : db.queryForList("select id, name, position from employee where status = 'ACTIVE' order by id")) {
            long employeeId = ((Number) person.get("id")).longValue();
            var held = db.queryForList("select es.skill_id from employee_skill es where es.employee_id = ? and es.skill_id in (" +
                    String.join(",", bySkill.keySet().stream().map(String::valueOf).toList()) + ")", employeeId);
            // 逐技能核对等级（IN 列表只做粗筛）/ verify levels per skill after the IN-filter
            var keySkills = held.stream().map(h -> ((Number) h.get("skill_id")).longValue())
                    .filter(skillId -> !db.queryForList("select 1 from employee_skill where employee_id = ? and skill_id = ? and level >= ?",
                            employeeId, skillId, bySkill.get(skillId).get("requiredLevel")).isEmpty())
                    .map(bySkill::get).toList();
            if (keySkills.isEmpty()) continue;
            var projects = db.queryForList("""
                    select count(distinct a.project_id) project_count from resource_allocation a
                    where a.employee_id = ? and a.status in ('PLANNED','CONFIRMED') and a.start_date <= ? and a.end_date >= ?""", employeeId, end, start);
            int[] load = loads.getOrDefault(employeeId, new int[2]);
            result.add(Map.of("employeeId", employeeId, "employeeName", person.get("name"),
                    "position", person.get("position") == null ? "" : person.get("position"),
                    "keySkills", keySkills, "projectCount", ((Number) projects.getFirst().get("project_count")).intValue(),
                    "loadedHours", load[0]));
        }
        result.sort((a, b) -> {
            int c = Integer.compare(((List<?>) b.get("keySkills")).size(), ((List<?>) a.get("keySkills")).size());
            return c != 0 ? c : Integer.compare(((Number) b.get("loadedHours")).intValue(), ((Number) a.get("loadedHours")).intValue());
        });
        return result.size() > 20 ? List.copyOf(result.subList(0, 20)) : result;
    }

    /** 核心人员离开影响：其窗口内任务逐技能检查可替代性 / what-if impact if a key person leaves. */
    @Transactional(readOnly = true)
    public Map<String, Object> leaveImpact(long employeeId) {
        var people = db.queryForList("select id, name from employee where id = ?", employeeId);
        if (people.isEmpty()) bad("员工不存在");
        LocalDate start = LocalDate.now(), end = start.plusWeeks(12);
        var affected = db.queryForList("""
                select distinct p.id project_id, p.name project_name, t.id task_id, t.name task_name,
                       t.start_date, t.end_date, t.estimated_hours
                from resource_allocation a
                join task t on t.id = a.task_id join project p on p.id = a.project_id
                where a.employee_id = ? and a.status in ('PLANNED','CONFIRMED') and a.start_date <= ? and a.end_date >= ?
                order by p.id, t.id""", employeeId, end, start);
        var result = new ArrayList<Map<String, Object>>();
        for (var task : affected) {
            var needs = db.queryForList("""
                    select s.name skill_name, r.min_level,
                           (select count(*) from employee_skill es join employee e on e.id = es.employee_id
                            where es.skill_id = r.skill_id and es.level >= r.min_level and e.status = 'ACTIVE' and e.id <> ?) other_qualified
                    from task_skill_requirement r join skill s on s.id = r.skill_id
                    where r.task_id = ? and r.requirement_type = 'REQUIRED'""", employeeId, task.get("task_id"));
            boolean replaceable = needs.stream().allMatch(n -> ((Number) n.get("other_qualified")).intValue() > 0);
            result.add(Map.of("projectId", ((Number) task.get("project_id")).longValue(), "projectName", task.get("project_name"),
                    "taskId", ((Number) task.get("task_id")).longValue(), "taskName", task.get("task_name"),
                    "startDate", task.get("start_date").toString(), "endDate", task.get("end_date").toString(),
                    "hours", ((Number) task.get("estimated_hours")).intValue(), "replaceable", replaceable,
                    "needs", needs.stream().map(n -> Map.of("skillName", n.get("skill_name"),
                            "minLevel", ((Number) n.get("min_level")).intValue(),
                            "otherQualified", ((Number) n.get("other_qualified")).intValue())).toList()));
        }
        long critical = result.stream().filter(r -> !((Boolean) r.get("replaceable"))).count();
        return Map.of("employeeId", employeeId, "employeeName", people.getFirst().get("name"),
                "affectedTasks", result, "criticalTasks", critical);
    }

    /** 窗口内每人 [生效分配占用工时, 不可用窗口工时] / per-person [allocated hours, unavailable hours] in the window. */
    private HashMap<Long, int[]> loads(LocalDate start, LocalDate end) {
        var loads = new HashMap<Long, int[]>();
        db.query("select employee_id, start_date, end_date, allocation from resource_allocation where status in ('PLANNED','CONFIRMED') and start_date <= ? and end_date >= ?",
                rs -> {
                    long employee = rs.getLong("employee_id");
                    var overlap = days(rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class))
                            .stream().filter(d -> !d.isBefore(start) && !d.isAfter(end)).toList();
                    int hours = (int) Math.round(overlap.size() * 8 * rs.getBigDecimal("allocation").doubleValue() / 100.0);
                    loads.computeIfAbsent(employee, k -> new int[2])[0] += hours;
                }, end, start);
        db.query("select employee_id, start_date, end_date from employee_availability where type <> 'AVAILABLE' and start_date <= ? and end_date >= ?",
                rs -> {
                    long employee = rs.getLong("employee_id");
                    var overlap = days(rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class))
                            .stream().filter(d -> !d.isBefore(start) && !d.isAfter(end)).toList();
                    loads.computeIfAbsent(employee, k -> new int[2])[1] += overlap.size() * 8;
                }, end, start);
        return loads;
    }
}
