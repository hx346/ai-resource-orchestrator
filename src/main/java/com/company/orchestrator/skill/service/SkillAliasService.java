package com.company.orchestrator.skill.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.skill.entity.SkillAlias;
import com.company.orchestrator.skill.mapper.SkillAliasMapper;

import lombok.RequiredArgsConstructor;

/** 技能别名管理 / Skill alias management. */
@Service
@RequiredArgsConstructor
public class SkillAliasService {

    private final SkillAliasMapper aliasMapper;
    private final SkillService skillService;

    public List<SkillAlias> listBySkill(Long skillId) {
        return aliasMapper.selectList(new LambdaQueryWrapper<SkillAlias>()
                .eq(SkillAlias::getSkillId, skillId)
                .orderByAsc(SkillAlias::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long skillId, String alias) {
        skillService.requireExists(skillId);
        SkillAlias skillAlias = new SkillAlias();
        skillAlias.setSkillId(skillId);
        skillAlias.setAlias(alias);
        aliasMapper.insert(skillAlias);
        return skillAlias.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        aliasMapper.deleteById(id);
    }
}
