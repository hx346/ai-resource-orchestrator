package com.company.orchestrator.skill.dto;

import java.math.BigDecimal;

/**
 * 技能草稿条目：未匹配技能库时 skillId 为空；suggested* 为确定性相似度给出的相似技能建议（仅建议）。
 * Draft skill item; skillId is null when unmatched. suggested* fields carry
 * an optional deterministic similarity hint, never an automatic mapping.
 */
public record AiSkillDraftItem(
        String name,
        Long skillId,
        String skillName,
        String matchedBy,
        int level,
        BigDecimal confidence,
        String reason,
        Long suggestedSkillId,
        String suggestedSkillName,
        Double similarity) {
}
