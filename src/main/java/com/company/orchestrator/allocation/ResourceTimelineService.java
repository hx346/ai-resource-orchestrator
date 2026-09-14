package com.company.orchestrator.allocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
/** Weekly capacity timeline across projects: per active employee, Monday-based weeks with summed allocation. / 全员周负荷时间线：按周一为界聚合各项目占用。 */
@Service @RequiredArgsConstructor
public class ResourceTimelineService {
    private final JdbcTemplate db;
    private static final int WEEKS=27;
    public Map<String,Object> timeline() {
        var bookings=db.queryForList("""
            select a.employee_id,a.start_date,a.end_date,a.allocation,a.status,p.name project_name,coalesce(t.name,'') task_name
            from resource_allocation a join project p on p.id=a.project_id left join task t on t.id=a.task_id
            where a.status in ('PLANNED','CONFIRMED') and a.end_date>=current_date order by a.employee_id,a.start_date,a.id
            """);
        var employees=db.queryForList("select id,name,weekly_hours from employee where status='ACTIVE' order by id limit 500");
        LocalDate today=LocalDate.now(), first=today.minusDays(today.getDayOfWeek().getValue()-1);
        var weeks=new ArrayList<String>();
        for(var d=first;weeks.size()<WEEKS;d=d.plusWeeks(1)) weeks.add(d.toString());
        var byEmployee=new HashMap<Long,List<Map<String,Object>>>();
        for(var b:bookings) {
            LocalDate start=date(b.get("start_date")), end=date(b.get("end_date"));
            byEmployee.computeIfAbsent(((Number)b.get("employee_id")).longValue(),k -> new ArrayList<>())
                .add(Map.of("start",start.toString(),"end",end.toString(),"allocation",((BigDecimal)b.get("allocation")).intValue(),
                    "status",b.get("status").toString(),"projectName",b.get("project_name").toString(),"taskName",b.get("task_name").toString()));
        }
        var rows=new ArrayList<Map<String,Object>>();
        for(var e:employees) {
            long id=((Number)e.get("id")).longValue();
            var mine=byEmployee.getOrDefault(id,List.of());
            var load=new TreeMap<String,Integer>();
            for(var b:mine) {
                var start=LocalDate.parse((String)b.get("start")); var end=LocalDate.parse((String)b.get("end"));
                for(String week:weeks) { var monday=LocalDate.parse(week); if(!start.isAfter(monday.plusDays(6)) && !end.isBefore(monday)) load.merge(week,(int)b.get("allocation"),Integer::sum); }
            }
            rows.add(Map.of("employeeId",id,"name",e.get("name"),"weeklyHours",e.get("weekly_hours"),"bookings",mine,"load",load));
        }
        return Map.of("weeks",weeks,"rows",rows);
    }
    private static LocalDate date(Object value) { return LocalDate.parse(value.toString()); }
}
