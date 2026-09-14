package com.company.orchestrator.skill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.orchestrator.common.enums.SkillSource;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 员工技能入参 / Employee skill upsert item. */
public record EmployeeSkillUpsertRequest(
        @NotNull Long skillId,
        @NotNull @Min(1) @Max(5) Integer level,
        @Min(0) Integer experienceMonths,
        SkillSource source,
        @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
        LocalDate lastUsedAt) {
}
