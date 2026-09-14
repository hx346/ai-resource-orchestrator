package com.company.orchestrator.skill.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.skill.entity.SkillAlias;
import com.company.orchestrator.skill.mapper.SkillAliasMapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;

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
    public void delete(Long skillId, Long id) {
        SkillAlias alias = aliasMapper.selectById(id);
        if (alias == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "别名不存在");
        if (!alias.getSkillId().equals(skillId)) throw new BusinessException(ErrorCode.BAD_REQUEST,"记录不属于指定资源");
        aliasMapper.deleteById(id);
    }
}
