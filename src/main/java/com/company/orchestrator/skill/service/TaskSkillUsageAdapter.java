package com.company.orchestrator.skill.service;
import com.company.orchestrator.skill.api.SkillUsagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Read-only reference lookup avoids a SkillService -> RequirementService -> SkillService cycle. */
@Component @RequiredArgsConstructor
public class TaskSkillUsageAdapter implements SkillUsagePort {
    private final JdbcTemplate db;
    public long countTaskRequirements(Long skillId) { return db.queryForObject("select count(*) from task_skill_requirement where skill_id=?",Long.class,skillId); }
}
