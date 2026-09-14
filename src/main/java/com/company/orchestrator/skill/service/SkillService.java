package com.company.orchestrator.skill.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.skill.api.SkillUsagePort;
import com.company.orchestrator.skill.dto.SkillUpsertRequest;
import com.company.orchestrator.skill.entity.EmployeeSkill;
import com.company.orchestrator.skill.entity.Skill;
import com.company.orchestrator.skill.mapper.EmployeeSkillMapper;
import com.company.orchestrator.skill.mapper.SkillMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 技能管理 / Skill management. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillMapper skillMapper;
    private final EmployeeSkillMapper employeeSkillMapper;
    private final SkillCategoryService categoryService;
    /** 由 project 模块实现，查询任务需求引用 / Implemented by the project module. */
    private final SkillUsagePort skillUsagePort;

    public IPage<Skill> page(long pageNum, long pageSize, String keyword, Long categoryId) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>()
                .like(StringUtils.hasText(keyword), Skill::getName, keyword)
                .eq(categoryId != null, Skill::getCategoryId, categoryId)
                .orderByAsc(Skill::getId);
        return skillMapper.selectPage(Page.of(Math.max(1,pageNum), Math.max(1,Math.min(500,pageSize))), wrapper);
    }

    public Skill requireExists(Long skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new BusinessException(ErrorCode.SKILL_NOT_FOUND, skillId);
        }
        return skill;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SkillUpsertRequest request) {
        categoryService.requireExists(request.categoryId());
        requireNameUnique(request.name(), null);

        Skill skill = new Skill();
        apply(skill, request);
        skill.setStatus(Skill.STATUS_ACTIVE);
        skillMapper.insert(skill);
        log.info("skill created, id={}, name={}", skill.getId(), skill.getName());
        return skill.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SkillUpsertRequest request) {
        Skill skill = requireExists(id);
        categoryService.requireExists(request.categoryId());
        requireNameUnique(request.name(), id);

        apply(skill, request);
        skillMapper.updateById(skill);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        long employeeSkillCount = employeeSkillMapper.selectCount(new LambdaQueryWrapper<EmployeeSkill>()
                .eq(EmployeeSkill::getSkillId, id));
        long requirementCount = skillUsagePort.countTaskRequirements(id);
        if (employeeSkillCount > 0 || requirementCount > 0) {
            throw new BusinessException(ErrorCode.SKILL_IN_USE, id);
        }
        skillMapper.deleteById(id);
    }

    private void requireNameUnique(String name, Long excludeId) {
        Skill existing = skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getName, name)
                .last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.DUPLICATE, name);
        }
    }

    private void apply(Skill skill, SkillUpsertRequest request) {
        Long parent=request.parentId(); var seen=new java.util.HashSet<Long>();
        while(parent!=null) {
            if(parent.equals(skill.getId()) || !seen.add(parent)) throw new BusinessException(ErrorCode.BAD_REQUEST,"技能层级存在循环");
            parent=requireExists(parent).getParentId();
        }
        skill.setCategoryId(request.categoryId());
        skill.setParentId(request.parentId());
        skill.setName(request.name());
        skill.setDescription(request.description());
    }
}
