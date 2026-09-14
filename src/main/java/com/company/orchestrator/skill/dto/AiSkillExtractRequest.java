package com.company.orchestrator.skill.dto;

import com.company.orchestrator.common.enums.SkillSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 技能识别入参：经历文本与其来源 / AI skill extraction input: experience text and its origin. */
public record AiSkillExtractRequest(
        @NotBlank @Size(max = 20000) String text,
        SkillSource source) {
}
