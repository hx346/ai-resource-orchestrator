package com.company.orchestrator.skill.dto;

import com.company.orchestrator.skill.entity.Skill;

/** 技能视图 / Skill view. */
public record SkillView(Long id, Long categoryId, Long parentId, String name, String description, String status) {

    public static SkillView from(Skill skill) {
        return new SkillView(skill.getId(), skill.getCategoryId(), skill.getParentId(),
                skill.getName(), skill.getDescription(), skill.getStatus());
    }
}
