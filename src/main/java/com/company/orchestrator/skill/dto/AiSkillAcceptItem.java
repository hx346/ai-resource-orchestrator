package com.company.orchestrator.skill.dto;

import java.math.BigDecimal;

import com.company.orchestrator.common.enums.SkillSource;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 人工确认写入画像的草稿条目 / Human-confirmed draft item to merge into the profile. */
public record AiSkillAcceptItem(
        @NotNull Long skillId,
        @NotNull @Min(1) @Max(5) Integer level,
        SkillSource source,
        @DecimalMin("0") @DecimalMax("1") BigDecimal confidence) {
}
