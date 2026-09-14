package com.company.orchestrator.skill;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.skill.entity.Skill;
import com.company.orchestrator.skill.entity.SkillAlias;
import com.company.orchestrator.skill.mapper.SkillAliasMapper;
import com.company.orchestrator.skill.mapper.SkillMapper;
import com.company.orchestrator.skill.service.SkillNormalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技能归一化单元测试：精确名称匹配 → Skill Alias 匹配 → 未命中。
 * Skill normalization tests: exact match, alias match, miss.
 */
@ExtendWith(MockitoExtension.class)
class SkillNormalizerTest {

    @Mock
    private SkillMapper skillMapper;

    @Mock
    private SkillAliasMapper aliasMapper;

    private SkillNormalizer skillNormalizer;

    @BeforeEach
    void setUp() {
        skillNormalizer = new SkillNormalizer(skillMapper, aliasMapper);
    }

    @Test
    @DisplayName("精确名称命中：返回 EXACT，不再查别名 / Exact name match returns EXACT without alias lookup")
    void normalizeExactMatch() {
        Skill skill = new Skill();
        skill.setId(1L);
        skill.setName("计算机视觉");
        when(skillMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        Optional<SkillNormalizer.NormalizedSkill> result = skillNormalizer.normalize("计算机视觉");

        assertThat(result).isPresent();
        assertThat(result.get().skillId()).isEqualTo(1L);
        assertThat(result.get().canonicalName()).isEqualTo("计算机视觉");
        assertThat(result.get().matchedBy()).isEqualTo(SkillNormalizer.MATCHED_BY_EXACT);
        verify(aliasMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("别名命中：返回 ALIAS 与标准技能 / Alias match returns ALIAS and the canonical skill")
    void normalizeAliasMatch() {
        when(skillMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SkillAlias alias = new SkillAlias();
        alias.setSkillId(2L);
        alias.setAlias("CV");
        when(aliasMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(alias);
        Skill skill = new Skill();
        skill.setId(2L);
        skill.setName("计算机视觉");
        when(skillMapper.selectById(2L)).thenReturn(skill);

        Optional<SkillNormalizer.NormalizedSkill> result = skillNormalizer.normalize("CV");

        assertThat(result).isPresent();
        assertThat(result.get().skillId()).isEqualTo(2L);
        assertThat(result.get().canonicalName()).isEqualTo("计算机视觉");
        assertThat(result.get().matchedBy()).isEqualTo(SkillNormalizer.MATCHED_BY_ALIAS);
    }

    @Test
    @DisplayName("未命中返回 empty / Unmatched name returns empty")
    void normalizeMiss() {
        when(skillMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(aliasMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        lenient().when(skillMapper.selectById(anyLong())).thenReturn(null);

        assertThat(skillNormalizer.normalize("不存在的技能")).isEmpty();
    }

    @Test
    @DisplayName("空白输入返回 empty / Blank input returns empty")
    void normalizeBlank() {
        assertThat(skillNormalizer.normalize("  ")).isEmpty();
        assertThat(skillNormalizer.normalize(null)).isEmpty();
        verify(skillMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }
}
