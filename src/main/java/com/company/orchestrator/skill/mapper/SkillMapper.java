package com.company.orchestrator.skill.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.skill.entity.Skill;

@Mapper
public interface SkillMapper extends BaseMapper<Skill> {
}
