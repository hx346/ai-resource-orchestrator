package com.company.orchestrator.skill.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 技能分类创建/更新入参 / Skill category create/update request. */
public record SkillCategoryUpsertRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 64) String code,
        @NotNull @Min(0) @Max(9999) Integer sortOrder) {
}
