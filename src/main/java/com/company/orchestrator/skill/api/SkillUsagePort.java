package com.company.orchestrator.skill.api;

/**
 * 技能被引用情况的查询端口（依赖倒置：project 模块实现，避免 skill ↔ project 循环依赖）。
 * Port for querying skill usage; implemented by the project module to avoid a cyclic dependency.
 */
public interface SkillUsagePort {

    /** 统计引用该技能的任务需求数量 / Count task skill requirements referencing the skill. */
    long countTaskRequirements(Long skillId);
}
