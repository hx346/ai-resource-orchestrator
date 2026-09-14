package com.company.orchestrator.skill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.orchestrator.common.enums.SkillSource;
import com.company.orchestrator.skill.entity.EmployeeSkill;

/** 员工技能视图（含技能名）/ Employee skill view with skill name. */
public record EmployeeSkillView(
        Long skillId,
        String skillName,
        Integer level,
        Integer experienceMonths,
        SkillSource source,
        BigDecimal confidence,
        Boolean verified,
        LocalDate lastUsedAt) {

    public static EmployeeSkillView of(EmployeeSkill employeeSkill, String skillName) {
        return new EmployeeSkillView(employeeSkill.getSkillId(), skillName, employeeSkill.getLevel(),
                employeeSkill.getExperienceMonths(), employeeSkill.getSource(),
                employeeSkill.getConfidence(), employeeSkill.getVerified(), employeeSkill.getLastUsedAt());
    }
}
