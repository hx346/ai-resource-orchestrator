package com.company.orchestrator.skill.dto;

import com.company.orchestrator.skill.entity.SkillCategory;

/** 技能分类视图 / Skill category view. */
public record SkillCategoryView(Long id, String name, String code, Integer sortOrder, String status) {

    public static SkillCategoryView from(SkillCategory category) {
        return new SkillCategoryView(category.getId(), category.getName(), category.getCode(),
                category.getSortOrder(), category.getStatus());
    }
}
