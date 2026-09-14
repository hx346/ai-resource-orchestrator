package com.company.orchestrator.system;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.company.orchestrator.ai.AiClient;
import static com.company.orchestrator.solver.PlanningRepository.bad;
@Service @RequiredArgsConstructor
public class DemoDataService {
    private final JdbcTemplate db;
    private final AiClient ai;
    @Transactional(rollbackFor=Exception.class)
    public void seed() {
        if(!"demo".equals(ai.mode())) bad("仅演示模式允许载入示例数据");
        db.execute("lock table employee,department,skill,skill_category,employee_skill in share row exclusive mode");
        if(db.queryForObject("select count(*) from employee",Long.class)>0) bad("已有员工数据，不能重复导入演示数据");
        long department=db.queryForObject("insert into department(name,code) values ('产品与研发','DEMO') returning id",Long.class);
        long category=db.queryForObject("insert into skill_category(name,code) values ('工程能力','DEMO') returning id",Long.class);
        long skill=db.queryForObject("insert into skill(category_id,name,description) values (?,'Java','后端工程与接口开发') on conflict(name) do update set name=excluded.name returning id",Long.class,category);
        String[] names={"陈知远","林予安","周亦宁","许清禾","沈一舟","方可言","陆景行","宋雨桐","江予白","叶书宁"};
        for(int i=0;i<names.length;i++) {
            long id=db.queryForObject("insert into employee(employee_no,name,department_id,position,location) values (?,?,?,'研发工程师','上海') returning id",Long.class,"DEMO-"+(i+1),names[i],department);
            db.update("insert into employee_skill(employee_id,skill_id,level,source,verified) values (?,?,?,'MANAGER',true)",id,skill,3+i%3);
        }
    }
}
