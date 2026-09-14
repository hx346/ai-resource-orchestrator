package com.company.orchestrator.project.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.orchestrator.project.entity.Project;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
