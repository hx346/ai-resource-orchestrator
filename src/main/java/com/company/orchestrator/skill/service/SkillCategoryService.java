package com.company.orchestrator.skill.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.skill.dto.SkillCategoryUpsertRequest;
import com.company.orchestrator.skill.entity.SkillCategory;
import com.company.orchestrator.skill.mapper.SkillCategoryMapper;

import lombok.RequiredArgsConstructor;

/** 技能分类管理 / Skill category management. */
@Service
@RequiredArgsConstructor
public class SkillCategoryService {

    private final SkillCategoryMapper categoryMapper;

    public List<SkillCategory> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<SkillCategory>()
                .orderByAsc(SkillCategory::getSortOrder));
    }

    public SkillCategory requireExists(Long categoryId) {
        SkillCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.SKILL_CATEGORY_NOT_FOUND, categoryId);
        }
        return category;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SkillCategoryUpsertRequest request) {
        SkillCategory category = new SkillCategory();
        category.setName(request.name());
        category.setCode(request.code());
        category.setSortOrder(request.sortOrder());
        category.setStatus(SkillCategory.STATUS_ACTIVE);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SkillCategoryUpsertRequest request) {
        SkillCategory category = requireExists(id);
        category.setName(request.name());
        category.setCode(request.code());
        category.setSortOrder(request.sortOrder());
        categoryMapper.updateById(category);
    }
}
