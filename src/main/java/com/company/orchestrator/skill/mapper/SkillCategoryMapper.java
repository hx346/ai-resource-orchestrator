package com.company.orchestrator.skill.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.skill.entity.SkillCategory;

@Mapper
public interface SkillCategoryMapper extends BaseMapper<SkillCategory> {
}
