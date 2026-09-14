package com.company.orchestrator.project.dto;

import java.math.BigDecimal;

import com.company.orchestrator.common.enums.RequirementType;
import com.company.orchestrator.project.entity.TaskSkillRequirement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 任务技能需求创建入参 / Task skill requirement request. */
public record TaskSkillRequirementRequest(
        @NotNull Long skillId,
        @NotNull @Min(1) @Max(5) Integer minLevel,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal weight,
        RequirementType requirementType) {

    public static TaskSkillRequirementRequest from(TaskSkillRequirement requirement) {
        return new TaskSkillRequirementRequest(requirement.getSkillId(), requirement.getMinLevel(),
                requirement.getWeight(), requirement.getRequirementType());
    }
}
