package com.company.orchestrator.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 技能创建/更新入参 / Skill create/update request. */
public record SkillUpsertRequest(
        @NotNull Long categoryId,
        Long parentId,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description) {
}
