package com.company.orchestrator.skill.dto;

import java.math.BigDecimal;

/** 技能草稿条目：未匹配技能库时 skillId 为空 / Draft skill item; skillId is null when unmatched. */
public record AiSkillDraftItem(
        String name,
        Long skillId,
        String skillName,
        String matchedBy,
        int level,
        BigDecimal confidence,
        String reason) {
}
