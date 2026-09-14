package com.company.orchestrator.skill.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.skill.entity.Skill;
import com.company.orchestrator.skill.entity.SkillAlias;
import com.company.orchestrator.skill.mapper.SkillAliasMapper;
import com.company.orchestrator.skill.mapper.SkillMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 技能名称归一化：精确名称匹配 → Skill Alias 匹配 →（未匹配返回空）。
 * 后续阶段再引入语义相似度与 AI 辅助判断。
 * Skill name normalization: exact match, then alias match. Semantic
 * similarity and AI-assisted judgment come in later phases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillNormalizer {

    public static final String MATCHED_BY_EXACT = "EXACT";
    public static final String MATCHED_BY_ALIAS = "ALIAS";

    private final SkillMapper skillMapper;
    private final SkillAliasMapper aliasMapper;

    /**
     * 归一化技能名称；未命中返回 empty（由调用方决定是否新建或转 AI 判断）。
     * Normalize a raw skill name; empty when nothing matches.
     */
    public Optional<NormalizedSkill> normalize(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            return Optional.empty();
        }
        String trimmed = rawName.trim();

        Skill exact = skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getName, trimmed)
                .last("LIMIT 1"));
        if (exact != null) {
            return Optional.of(new NormalizedSkill(exact.getId(), exact.getName(), MATCHED_BY_EXACT));
        }

        SkillAlias alias = aliasMapper.selectOne(new LambdaQueryWrapper<SkillAlias>()
                .eq(SkillAlias::getAlias, trimmed)
                .last("LIMIT 1"));
        if (alias != null) {
            Skill skill = skillMapper.selectById(alias.getSkillId());
            if (skill != null) {
                return Optional.of(new NormalizedSkill(skill.getId(), skill.getName(), MATCHED_BY_ALIAS));
            }
        }
        log.debug("skill name not normalized, rawName={}", trimmed);
        return Optional.empty();
    }

    /** 归一化结果 / Normalization result. */
    public record NormalizedSkill(Long skillId, String canonicalName, String matchedBy) {
    }
}
